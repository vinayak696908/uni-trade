package com.unitrade.service;

import com.unitrade.dto.ProductDtos.PageResponse;
import com.unitrade.dto.ProductDtos.ProductDto;
import com.unitrade.dto.ProductDtos.ProductRequest;
import com.unitrade.dto.ProductDtos.ProductSummaryDto;
import com.unitrade.entity.Campuses;
import com.unitrade.entity.ListingStatus;
import com.unitrade.entity.Product;
import com.unitrade.entity.ProductImage;
import com.unitrade.entity.User;
import com.unitrade.exception.ApiException;
import com.unitrade.repository.ProductRepository;
import com.unitrade.repository.WishlistRepository;
import com.unitrade.security.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProductService {

    public static final int MAX_IMAGES_PER_PRODUCT = 5;
    public static final int MAX_PAGE_SIZE = 48;

    private final ProductRepository productRepository;
    private final WishlistRepository wishlistRepository;
    private final CurrentUserService currentUserService;
    private final ImageStorageService imageStorageService;
    private final DtoMapper mapper;

    public ProductService(ProductRepository productRepository, WishlistRepository wishlistRepository,
                          CurrentUserService currentUserService, ImageStorageService imageStorageService,
                          DtoMapper mapper) {
        this.productRepository = productRepository;
        this.wishlistRepository = wishlistRepository;
        this.currentUserService = currentUserService;
        this.imageStorageService = imageStorageService;
        this.mapper = mapper;
    }

    // ---------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryDto> search(ProductSearchCriteria criteria, String sort, int page, int size) {
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw ApiException.badRequest("Minimum price cannot be greater than maximum price.");
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<Product> result = productRepository.findAll(criteria.toSpecification(),
                PageRequest.of(safePage, safeSize, ProductSearchCriteria.sortFor(sort)));
        return new PageResponse<>(
                result.getContent().stream().map(mapper::toSummary).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    /**
     * AVAILABLE and SOLD listings are public. HIDDEN (moderated) listings are visible only to their
     * owner and to admins; everyone else gets 404 so their existence is not revealed.
     */
    @Transactional(readOnly = true)
    public ProductDto getById(Long id) {
        Product product = findOrThrow(id);
        User viewer = currentUserService.optionalUser().orElse(null);
        if (product.getStatus() == ListingStatus.HIDDEN && !canManage(product, viewer)) {
            throw ApiException.notFound("Listing not found.");
        }
        boolean wishlisted = viewer != null && wishlistRepository.existsByUserIdAndProductId(viewer.getId(), id);
        return mapper.toDetail(product, viewer, wishlisted);
    }

    @Transactional(readOnly = true)
    public List<ProductSummaryDto> myListings() {
        User user = currentUserService.requireUser();
        return productRepository.findBySellerIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(mapper::toSummary)
                .toList();
    }

    // ---------------------------------------------------------------- writes

    @Transactional
    public ProductDto create(ProductRequest request) {
        User seller = currentUserService.requireUser();
        Product product = new Product();
        product.setSeller(seller);
        product.setStatus(ListingStatus.AVAILABLE);
        apply(product, request);
        productRepository.saveAndFlush(product);
        return mapper.toDetail(product, seller, false);
    }

    @Transactional
    public ProductDto update(Long id, ProductRequest request) {
        User user = currentUserService.requireUser();
        Product product = findOrThrow(id);
        requireManage(product, user);
        apply(product, request);
        productRepository.saveAndFlush(product);
        return mapper.toDetail(product, user, wishlistRepository.existsByUserIdAndProductId(user.getId(), id));
    }

    /** Seller-facing status change: AVAILABLE <-> SOLD. Moderated (HIDDEN) listings cannot be re-listed by the seller. */
    @Transactional
    public ProductDto updateStatus(Long id, ListingStatus newStatus) {
        User user = currentUserService.requireUser();
        Product product = findOrThrow(id);
        requireManage(product, user);
        if (newStatus == ListingStatus.HIDDEN) {
            throw ApiException.badRequest("Only moderators can hide a listing.");
        }
        if (product.getStatus() == ListingStatus.HIDDEN && !user.isAdmin()) {
            throw ApiException.forbidden("This listing was hidden by a moderator and cannot be changed.");
        }
        product.setStatus(newStatus);
        productRepository.saveAndFlush(product);
        return mapper.toDetail(product, user, wishlistRepository.existsByUserIdAndProductId(user.getId(), id));
    }

    /** Admin moderation: may set any status, including HIDDEN. */
    @Transactional
    public ProductDto moderate(Long id, ListingStatus newStatus) {
        User admin = currentUserService.requireAdmin();
        Product product = findOrThrow(id);
        product.setStatus(newStatus);
        productRepository.saveAndFlush(product);
        return mapper.toDetail(product, admin, false);
    }

    @Transactional
    public void delete(Long id) {
        User user = currentUserService.requireUser();
        Product product = findOrThrow(id);
        requireManage(product, user);
        List<String> files = product.getImages().stream().map(ProductImage::getStoredFilename).toList();
        productRepository.delete(product);
        deleteFilesAfterCommit(files);
    }

    @Transactional
    public ProductDto addImages(Long id, List<MultipartFile> files) {
        User user = currentUserService.requireUser();
        Product product = findOrThrow(id);
        requireManage(product, user);
        List<MultipartFile> nonEmpty = files == null ? List.of()
                : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (nonEmpty.isEmpty()) {
            throw ApiException.badRequest("Select at least one image to upload.");
        }
        if (product.getImages().size() + nonEmpty.size() > MAX_IMAGES_PER_PRODUCT) {
            throw ApiException.badRequest("A listing can have at most " + MAX_IMAGES_PER_PRODUCT + " images.");
        }

        List<String> storedSoFar = new ArrayList<>();
        try {
            int sortOrder = product.getImages().stream().mapToInt(ProductImage::getSortOrder).max().orElse(-1) + 1;
            for (MultipartFile file : nonEmpty) {
                ImageStorageService.StoredImage stored = imageStorageService.store(file);
                storedSoFar.add(stored.storedFilename());
                ProductImage image = new ProductImage();
                image.setStoredFilename(stored.storedFilename());
                image.setOriginalFilename(stored.originalFilename());
                image.setContentType(stored.contentType());
                image.setSizeBytes(stored.sizeBytes());
                image.setSortOrder(sortOrder++);
                product.addImage(image);
            }
            productRepository.saveAndFlush(product);
        } catch (RuntimeException e) {
            // Roll back files written to disk if any image in the batch is rejected.
            storedSoFar.forEach(imageStorageService::delete);
            throw e;
        }
        return mapper.toDetail(product, user, wishlistRepository.existsByUserIdAndProductId(user.getId(), id));
    }

    @Transactional
    public ProductDto deleteImage(Long productId, Long imageId) {
        User user = currentUserService.requireUser();
        Product product = findOrThrow(productId);
        requireManage(product, user);
        ProductImage image = product.getImages().stream()
                .filter(img -> img.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Image not found on this listing."));
        product.getImages().remove(image);
        productRepository.saveAndFlush(product);
        deleteFilesAfterCommit(List.of(image.getStoredFilename()));
        return mapper.toDetail(product, user, wishlistRepository.existsByUserIdAndProductId(user.getId(), productId));
    }

    // ---------------------------------------------------------------- helpers

    public Product findOrThrow(Long id) {
        return productRepository.findById(id).orElseThrow(() -> ApiException.notFound("Listing not found."));
    }

    private static boolean canManage(Product product, User user) {
        return user != null && (product.isOwnedBy(user) || user.isAdmin());
    }

    /**
     * Ownership check enforced on the server for every write. Changing the ID in the URL does not help:
     * the listing is loaded from the database and its real owner is compared with the session user.
     */
    private static void requireManage(Product product, User user) {
        if (!canManage(product, user)) {
            throw ApiException.forbidden("You can only modify your own listings.");
        }
    }

    private static void apply(Product product, ProductRequest request) {
        if (!Campuses.isValid(request.campus())) {
            throw ApiException.badRequest("Please choose a campus from the list.");
        }
        product.setTitle(TextSanitizer.singleLine(request.title()));
        product.setDescription(TextSanitizer.clean(request.description()));
        product.setCategory(request.category());
        product.setCondition(request.condition());
        product.setPrice(request.price());
        product.setCampus(request.campus());
    }

    private void deleteFilesAfterCommit(List<String> filenames) {
        if (filenames.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    filenames.forEach(imageStorageService::delete);
                }
            });
        } else {
            filenames.forEach(imageStorageService::delete);
        }
    }
}
