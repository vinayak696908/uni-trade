package com.unitrade.dto;

import com.unitrade.entity.ListingStatus;
import com.unitrade.entity.ReportReason;
import com.unitrade.entity.ReportStatus;
import com.unitrade.entity.Role;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class AdminDtos {

    private AdminDtos() {
    }

    /** Submitted by any logged-in student who wants to flag a listing. */
    public record CreateReportRequest(
            @NotNull(message = "Reason is required")
            ReportReason reason,

            @Size(max = 1000, message = "Details must be at most 1000 characters")
            String details
    ) {
    }

    public record ResolveReportRequest(
            @NotNull(message = "Resolution status is required")
            ReportStatus status,

            @Size(max = 500, message = "Note must be at most 500 characters")
            String adminNote,

            /** When true the reported listing is hidden from the marketplace. */
            boolean hideListing
    ) {
    }

    public record ReportDto(
            Long id,
            Long productId,
            String productTitle,
            ListingStatus productStatus,
            String sellerName,
            String sellerEmail,
            String reporterName,
            ReportReason reason,
            String details,
            ReportStatus status,
            String adminNote,
            String resolvedByName,
            LocalDateTime resolvedAt,
            LocalDateTime createdAt
    ) {
    }

    /** Confirmation returned to the student who filed a report (no admin-only fields). */
    public record ReportReceiptDto(Long id, ReportStatus status, LocalDateTime createdAt) {
    }

    public record AdminUserDto(
            Long id,
            String fullName,
            String email,
            String campus,
            Role role,
            boolean enabled,
            long listingCount,
            LocalDateTime createdAt
    ) {
    }

    public record UserStatusRequest(@NotNull(message = "enabled is required") Boolean enabled) {
    }

    public record ModerateListingRequest(@NotNull(message = "Status is required") ListingStatus status) {
    }

    public record AdminStatsDto(
            long totalUsers,
            long availableListings,
            long soldListings,
            long hiddenListings,
            long pendingReports
    ) {
    }
}
