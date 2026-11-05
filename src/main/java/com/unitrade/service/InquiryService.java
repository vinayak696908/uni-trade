package com.unitrade.service;

import com.unitrade.dto.InquiryDtos.InquiryDetailDto;
import com.unitrade.dto.InquiryDtos.InquirySummaryDto;
import com.unitrade.entity.Inquiry;
import com.unitrade.entity.InquiryStatus;
import com.unitrade.entity.ListingStatus;
import com.unitrade.entity.Product;
import com.unitrade.entity.User;
import com.unitrade.exception.ApiException;
import com.unitrade.repository.InquiryRepository;
import com.unitrade.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Buyer-seller conversations. Every read and write verifies that the current user is the buyer or
 * the seller of the inquiry; anyone else (including admins) receives 404 so private conversations
 * cannot be discovered by guessing IDs.
 */
@Service
public class InquiryService {

    private final InquiryRepository inquiryRepository;
    private final ProductService productService;
    private final CurrentUserService currentUserService;
    private final DtoMapper mapper;

    public InquiryService(InquiryRepository inquiryRepository, ProductService productService,
                          CurrentUserService currentUserService, DtoMapper mapper) {
        this.inquiryRepository = inquiryRepository;
        this.productService = productService;
        this.currentUserService = currentUserService;
        this.mapper = mapper;
    }

    /**
     * Starts a conversation with the seller. If the buyer already has an open conversation about this
     * listing, the message is appended to it instead of creating a duplicate thread.
     */
    @Transactional
    public InquiryDetailDto create(Long productId, String message) {
        User buyer = currentUserService.requireUser();
        Product product = productService.findOrThrow(productId);
        if (product.getStatus() == ListingStatus.HIDDEN) {
            throw ApiException.notFound("Listing not found.");
        }
        if (product.getStatus() != ListingStatus.AVAILABLE) {
            throw ApiException.badRequest("This item is no longer available.");
        }
        if (product.isOwnedBy(buyer)) {
            throw ApiException.badRequest("You cannot send an inquiry about your own listing.");
        }
        String body = TextSanitizer.clean(message);
        Inquiry inquiry = inquiryRepository
                .findFirstByProductIdAndBuyerIdAndStatus(productId, buyer.getId(), InquiryStatus.OPEN)
                .orElseGet(() -> {
                    Inquiry created = new Inquiry();
                    created.setProduct(product);
                    created.setBuyer(buyer);
                    created.setSeller(product.getSeller());
                    created.setStatus(InquiryStatus.OPEN);
                    return created;
                });
        inquiry.addMessage(buyer, body);
        inquiryRepository.saveAndFlush(inquiry);
        return mapper.toDetail(inquiry, buyer);
    }

    @Transactional(readOnly = true)
    public List<InquirySummaryDto> sent() {
        User user = currentUserService.requireUser();
        return inquiryRepository.findSentBy(user.getId()).stream().map(i -> mapper.toSummary(i, user)).toList();
    }

    @Transactional(readOnly = true)
    public List<InquirySummaryDto> received() {
        User user = currentUserService.requireUser();
        return inquiryRepository.findReceivedBy(user.getId()).stream().map(i -> mapper.toSummary(i, user)).toList();
    }

    @Transactional(readOnly = true)
    public InquiryDetailDto get(Long id) {
        User user = currentUserService.requireUser();
        return mapper.toDetail(findForParticipant(id, user), user);
    }

    @Transactional
    public InquiryDetailDto reply(Long id, String message) {
        User user = currentUserService.requireUser();
        Inquiry inquiry = findForParticipant(id, user);
        if (inquiry.getStatus() == InquiryStatus.CLOSED) {
            throw ApiException.badRequest("This conversation is closed.");
        }
        inquiry.addMessage(user, TextSanitizer.clean(message));
        inquiryRepository.saveAndFlush(inquiry);
        return mapper.toDetail(inquiry, user);
    }

    @Transactional
    public InquiryDetailDto close(Long id) {
        User user = currentUserService.requireUser();
        Inquiry inquiry = findForParticipant(id, user);
        inquiry.setStatus(InquiryStatus.CLOSED);
        inquiryRepository.saveAndFlush(inquiry);
        return mapper.toDetail(inquiry, user);
    }

    private Inquiry findForParticipant(Long id, User user) {
        Inquiry inquiry = inquiryRepository.findWithParticipants(id)
                .orElseThrow(() -> ApiException.notFound("Conversation not found."));
        if (!inquiry.isParticipant(user)) {
            throw ApiException.notFound("Conversation not found.");
        }
        return inquiry;
    }
}
