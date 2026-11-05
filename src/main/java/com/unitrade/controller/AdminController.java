package com.unitrade.controller;

import com.unitrade.dto.AdminDtos.AdminStatsDto;
import com.unitrade.dto.AdminDtos.AdminUserDto;
import com.unitrade.dto.AdminDtos.ModerateListingRequest;
import com.unitrade.dto.AdminDtos.ReportDto;
import com.unitrade.dto.AdminDtos.ResolveReportRequest;
import com.unitrade.dto.AdminDtos.UserStatusRequest;
import com.unitrade.dto.ProductDtos.PageResponse;
import com.unitrade.dto.ProductDtos.ProductDto;
import com.unitrade.dto.ProductDtos.ProductSummaryDto;
import com.unitrade.entity.ListingStatus;
import com.unitrade.entity.ReportStatus;
import com.unitrade.security.CurrentUserService;
import com.unitrade.service.ModerationService;
import com.unitrade.service.ProductSearchCriteria;
import com.unitrade.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Every endpoint here requires ROLE_ADMIN (enforced by SecurityConfig for /api/admin/** and
 * re-checked against the database in the service layer).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ModerationService moderationService;
    private final ProductService productService;
    private final CurrentUserService currentUserService;

    public AdminController(ModerationService moderationService, ProductService productService,
                           CurrentUserService currentUserService) {
        this.moderationService = moderationService;
        this.productService = productService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/stats")
    public AdminStatsDto stats() {
        return moderationService.stats();
    }

    @GetMapping("/reports")
    public List<ReportDto> reports(@RequestParam(required = false) ReportStatus status) {
        return moderationService.reports(status);
    }

    @PatchMapping("/reports/{id}")
    public ReportDto resolveReport(@PathVariable Long id, @Valid @RequestBody ResolveReportRequest request) {
        return moderationService.resolve(id, request);
    }

    /** All listings regardless of status, for review. */
    @GetMapping("/products")
    public PageResponse<ProductSummaryDto> products(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ListingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        currentUserService.requireAdmin();
        List<ListingStatus> statuses = status == null ? List.of() : List.of(status);
        ProductSearchCriteria criteria = new ProductSearchCriteria(q, null, null, null, null, null, statuses);
        return productService.search(criteria, "newest", page, size);
    }

    @PatchMapping("/products/{id}/status")
    public ProductDto moderateProduct(@PathVariable Long id, @Valid @RequestBody ModerateListingRequest request) {
        return productService.moderate(id, request.status());
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.delete(id); // service allows owner or admin
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users")
    public PageResponse<AdminUserDto> users(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return moderationService.users(q, page, size);
    }

    @PatchMapping("/users/{id}/status")
    public AdminUserDto setUserStatus(@PathVariable Long id, @Valid @RequestBody UserStatusRequest request) {
        return moderationService.setUserEnabled(id, request.enabled());
    }
}
