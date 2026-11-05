package com.unitrade.dto;

import com.unitrade.entity.Category;
import com.unitrade.entity.ItemCondition;
import com.unitrade.entity.ListingStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ProductDtos {

    private ProductDtos() {
    }

    /** Used for both creating and fully updating a listing. */
    public record ProductRequest(
            @NotBlank(message = "Title is required")
            @Size(min = 3, max = 150, message = "Title must be 3-150 characters")
            String title,

            @NotBlank(message = "Description is required")
            @Size(min = 10, max = 4000, message = "Description must be 10-4000 characters")
            String description,

            @NotNull(message = "Category is required")
            Category category,

            @NotNull(message = "Condition is required")
            ItemCondition condition,

            @NotNull(message = "Price is required")
            @DecimalMin(value = "1.00", message = "Price must be at least ₹1")
            @DecimalMax(value = "1000000.00", message = "Price must not exceed ₹10,00,000")
            @Digits(integer = 7, fraction = 2, message = "Price can have at most 2 decimal places")
            BigDecimal price,

            @NotBlank(message = "Campus is required")
            String campus
    ) {
    }

    public record StatusUpdateRequest(@NotNull(message = "Status is required") ListingStatus status) {
    }

    public record ImageDto(Long id, String url) {
    }

    /** Public seller information. Deliberately excludes email and phone. */
    public record SellerDto(Long id, String displayName, String campus, LocalDateTime memberSince) {
    }

    /** Compact form used in grids and lists. */
    public record ProductSummaryDto(
            Long id,
            String title,
            BigDecimal price,
            Category category,
            ItemCondition condition,
            String campus,
            ListingStatus status,
            String thumbnailUrl,
            Long sellerId,
            String sellerName,
            LocalDateTime createdAt
    ) {
    }

    /** Full listing detail. */
    public record ProductDto(
            Long id,
            String title,
            String description,
            BigDecimal price,
            Category category,
            ItemCondition condition,
            String campus,
            ListingStatus status,
            List<ImageDto> images,
            SellerDto seller,
            boolean owner,
            boolean wishlisted,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }
}
