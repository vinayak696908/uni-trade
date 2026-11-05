package com.unitrade.dto;

import com.unitrade.entity.InquiryStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class InquiryDtos {

    private InquiryDtos() {
    }

    public record CreateInquiryRequest(
            @NotNull(message = "Product is required")
            Long productId,

            @NotBlank(message = "Message is required")
            @Size(min = 2, max = 2000, message = "Message must be 2-2000 characters")
            String message
    ) {
    }

    public record MessageRequest(
            @NotBlank(message = "Message is required")
            @Size(min = 1, max = 2000, message = "Message must be at most 2000 characters")
            String message
    ) {
    }

    public record MessageDto(Long id, String senderName, boolean mine, String body, LocalDateTime createdAt) {
    }

    /** Row in the inbox list. {@code myRole} is BUYER or SELLER from the viewer's perspective. */
    public record InquirySummaryDto(
            Long id,
            Long productId,
            String productTitle,
            String productThumbnailUrl,
            String counterpartName,
            String myRole,
            InquiryStatus status,
            String lastMessagePreview,
            int messageCount,
            LocalDateTime lastMessageAt
    ) {
    }

    public record InquiryDetailDto(
            Long id,
            Long productId,
            String productTitle,
            String productThumbnailUrl,
            String buyerName,
            String sellerName,
            String myRole,
            InquiryStatus status,
            List<MessageDto> messages,
            LocalDateTime createdAt
    ) {
    }
}
