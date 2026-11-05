package com.unitrade.service;

import com.unitrade.dto.ProductDtos.ProductSummaryDto;
import com.unitrade.entity.ListingStatus;
import com.unitrade.entity.Product;
import com.unitrade.entity.User;
import com.unitrade.entity.WishlistItem;
import com.unitrade.exception.ApiException;
import com.unitrade.repository.WishlistRepository;
import com.unitrade.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final ProductService productService;
    private final CurrentUserService currentUserService;
    private final DtoMapper mapper;

    public WishlistService(WishlistRepository wishlistRepository, ProductService productService,
                           CurrentUserService currentUserService, DtoMapper mapper) {
        this.wishlistRepository = wishlistRepository;
        this.productService = productService;
        this.currentUserService = currentUserService;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<ProductSummaryDto> list() {
        User user = currentUserService.requireUser();
        return wishlistRepository.findVisibleForUser(user.getId(), ListingStatus.HIDDEN).stream()
                .map(WishlistItem::getProduct)
                .map(mapper::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Long> productIds() {
        User user = currentUserService.requireUser();
        return wishlistRepository.findProductIdsByUserId(user.getId());
    }

    /** Idempotent: adding an item that is already saved returns false and changes nothing. */
    @Transactional
    public boolean add(Long productId) {
        User user = currentUserService.requireUser();
        Product product = productService.findOrThrow(productId);
        if (product.getStatus() == ListingStatus.HIDDEN) {
            throw ApiException.notFound("Listing not found.");
        }
        if (product.isOwnedBy(user)) {
            throw ApiException.badRequest("You cannot add your own listing to your wishlist.");
        }
        if (wishlistRepository.existsByUserIdAndProductId(user.getId(), productId)) {
            return false;
        }
        wishlistRepository.save(new WishlistItem(user, product));
        return true;
    }

    @Transactional
    public void remove(Long productId) {
        User user = currentUserService.requireUser();
        WishlistItem item = wishlistRepository.findByUserIdAndProductId(user.getId(), productId)
                .orElseThrow(() -> ApiException.notFound("This listing is not in your wishlist."));
        wishlistRepository.delete(item);
    }
}
