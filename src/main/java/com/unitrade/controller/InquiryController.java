package com.unitrade.controller;

import com.unitrade.dto.InquiryDtos.CreateInquiryRequest;
import com.unitrade.dto.InquiryDtos.InquiryDetailDto;
import com.unitrade.dto.InquiryDtos.InquirySummaryDto;
import com.unitrade.dto.InquiryDtos.MessageRequest;
import com.unitrade.service.InquiryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inquiries")
public class InquiryController {

    private final InquiryService inquiryService;

    public InquiryController(InquiryService inquiryService) {
        this.inquiryService = inquiryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InquiryDetailDto create(@Valid @RequestBody CreateInquiryRequest request) {
        return inquiryService.create(request.productId(), request.message());
    }

    /** Conversations the current user started as a buyer. */
    @GetMapping("/sent")
    public List<InquirySummaryDto> sent() {
        return inquiryService.sent();
    }

    /** Conversations about the current user's listings. */
    @GetMapping("/received")
    public List<InquirySummaryDto> received() {
        return inquiryService.received();
    }

    @GetMapping("/{id}")
    public InquiryDetailDto get(@PathVariable Long id) {
        return inquiryService.get(id);
    }

    @PostMapping("/{id}/messages")
    public InquiryDetailDto reply(@PathVariable Long id, @Valid @RequestBody MessageRequest request) {
        return inquiryService.reply(id, request.message());
    }

    @PatchMapping("/{id}/close")
    public InquiryDetailDto close(@PathVariable Long id) {
        return inquiryService.close(id);
    }
}
