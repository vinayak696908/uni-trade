package com.unitrade.controller;

import com.unitrade.dto.AdminDtos.CreateReportRequest;
import com.unitrade.dto.AdminDtos.ReportReceiptDto;
import com.unitrade.dto.ProductDtos.PageResponse;
import com.unitrade.dto.ProductDtos.ProductDto;
import com.unitrade.dto.ProductDtos.ProductRequest;
import com.unitrade.dto.ProductDtos.ProductSummaryDto;
import com.unitrade.dto.ProductDtos.StatusUpdateRequest;
import com.unitrade.entity.Category;
import com.unitrade.entity.ItemCondition;
import com.unitrade.entity.ListingStatus;
import com.unitrade.service.ModerationService;
import com.unitrade.service.ProductSearchCriteria;
import com.unitrade.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final ModerationService moderationService;

    public ProductController(ProductService productService, ModerationService moderationService) {
        this.productService = productService;
        this.moderationService = moderationService;
    }

    /**
     * Public marketplace search. Only AVAILABLE listings are returned.
     * Example: GET /api/products?q=calculator&category=CALCULATORS&maxPrice=1500&sort=price_asc&page=0&size=12
     */
    @GetMapping
    public PageResponse<ProductSummaryDto> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) String campus,
            @RequestParam(required = false) ItemCondition condition,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false, defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        String query = q == null ? null : q.trim();
        if (query != null && query.length() > 100) {
            query = query.substring(0, 100);
        }
        ProductSearchCriteria criteria = new ProductSearchCriteria(
                query, category, campus, condition, minPrice, maxPrice, List.of(ListingStatus.AVAILABLE));
        return productService.search(criteria, sort, page, size);
    }

    @GetMapping("/{id}")
    public ProductDto get(@PathVariable Long id) {
        return productService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDto create(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    public ProductDto update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Mark as SOLD or back to AVAILABLE. */
    @PatchMapping("/{id}/status")
    public ProductDto updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return productService.updateStatus(id, request.status());
    }

    /** multipart/form-data with one or more parts named "images" (JPEG/PNG/WebP, max 5 MB each, 5 per listing). */
    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductDto uploadImages(@PathVariable Long id, @RequestPart("images") List<MultipartFile> images) {
        return productService.addImages(id, images);
    }

    @DeleteMapping("/{id}/images/{imageId}")
    public ProductDto deleteImage(@PathVariable Long id, @PathVariable Long imageId) {
        return productService.deleteImage(id, imageId);
    }

    @PostMapping("/{id}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportReceiptDto report(@PathVariable Long id, @Valid @RequestBody CreateReportRequest request) {
        return moderationService.report(id, request);
    }
}
