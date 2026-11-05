package com.unitrade.service;

import com.unitrade.dto.AdminDtos.ReportDto;
import com.unitrade.dto.InquiryDtos.InquiryDetailDto;
import com.unitrade.dto.InquiryDtos.InquirySummaryDto;
import com.unitrade.dto.InquiryDtos.MessageDto;
import com.unitrade.dto.ProductDtos.ImageDto;
import com.unitrade.dto.ProductDtos.ProductDto;
import com.unitrade.dto.ProductDtos.ProductSummaryDto;
import com.unitrade.dto.ProductDtos.SellerDto;
import com.unitrade.entity.Inquiry;
import com.unitrade.entity.InquiryMessage;
import com.unitrade.entity.ListingReport;
import com.unitrade.entity.Product;
import com.unitrade.entity.ProductImage;
import com.unitrade.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Converts entities to API DTOs. Must be called inside a transaction because it walks lazy associations.
 */
@Component
public class DtoMapper {

    public static final String IMAGE_URL_PREFIX = "/api/images/";

    public String imageUrl(ProductImage image) {
        return IMAGE_URL_PREFIX + image.getStoredFilename();
    }

    public String thumbnailUrl(Product product) {
        return product.getImages().isEmpty() ? null : imageUrl(product.getImages().get(0));
    }

    public ProductSummaryDto toSummary(Product p) {
        return new ProductSummaryDto(
                p.getId(), p.getTitle(), p.getPrice(), p.getCategory(), p.getCondition(), p.getCampus(),
                p.getStatus(), thumbnailUrl(p), p.getSeller().getId(), p.getSeller().getDisplayName(), p.getCreatedAt());
    }

    public ProductDto toDetail(Product p, User viewer, boolean wishlisted) {
        User seller = p.getSeller();
        List<ImageDto> images = p.getImages().stream()
                .map(img -> new ImageDto(img.getId(), imageUrl(img)))
                .toList();
        return new ProductDto(
                p.getId(), p.getTitle(), p.getDescription(), p.getPrice(), p.getCategory(), p.getCondition(),
                p.getCampus(), p.getStatus(), images,
                new SellerDto(seller.getId(), seller.getDisplayName(), seller.getCampus(), seller.getCreatedAt()),
                p.isOwnedBy(viewer), wishlisted, p.getCreatedAt(), p.getUpdatedAt());
    }

    public InquirySummaryDto toSummary(Inquiry inquiry, User viewer) {
        boolean viewerIsBuyer = inquiry.getBuyer().getId().equals(viewer.getId());
        User counterpart = viewerIsBuyer ? inquiry.getSeller() : inquiry.getBuyer();
        List<InquiryMessage> messages = inquiry.getMessages();
        String preview = messages.isEmpty() ? "" : truncate(messages.get(messages.size() - 1).getBody(), 120);
        Product product = inquiry.getProduct();
        return new InquirySummaryDto(
                inquiry.getId(), product.getId(), product.getTitle(), thumbnailUrl(product),
                counterpart.getDisplayName(), viewerIsBuyer ? "BUYER" : "SELLER", inquiry.getStatus(),
                preview, messages.size(), inquiry.getLastMessageAt());
    }

    public InquiryDetailDto toDetail(Inquiry inquiry, User viewer) {
        boolean viewerIsBuyer = inquiry.getBuyer().getId().equals(viewer.getId());
        List<MessageDto> messages = inquiry.getMessages().stream()
                .map(m -> new MessageDto(m.getId(), m.getSender().getDisplayName(),
                        m.getSender().getId().equals(viewer.getId()), m.getBody(), m.getCreatedAt()))
                .toList();
        Product product = inquiry.getProduct();
        return new InquiryDetailDto(
                inquiry.getId(), product.getId(), product.getTitle(), thumbnailUrl(product),
                inquiry.getBuyer().getDisplayName(), inquiry.getSeller().getDisplayName(),
                viewerIsBuyer ? "BUYER" : "SELLER", inquiry.getStatus(), messages, inquiry.getCreatedAt());
    }

    public ReportDto toDto(ListingReport r) {
        Product p = r.getProduct();
        return new ReportDto(
                r.getId(), p.getId(), p.getTitle(), p.getStatus(),
                p.getSeller().getFullName(), p.getSeller().getEmail(),
                r.getReporter().getFullName(), r.getReason(), r.getDetails(), r.getStatus(), r.getAdminNote(),
                r.getResolvedBy() == null ? null : r.getResolvedBy().getFullName(),
                r.getResolvedAt(), r.getCreatedAt());
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
