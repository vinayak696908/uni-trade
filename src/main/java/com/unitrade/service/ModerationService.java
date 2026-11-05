package com.unitrade.service;

import com.unitrade.dto.AdminDtos.AdminStatsDto;
import com.unitrade.dto.AdminDtos.AdminUserDto;
import com.unitrade.dto.AdminDtos.CreateReportRequest;
import com.unitrade.dto.AdminDtos.ReportDto;
import com.unitrade.dto.AdminDtos.ReportReceiptDto;
import com.unitrade.dto.AdminDtos.ResolveReportRequest;
import com.unitrade.dto.ProductDtos.PageResponse;
import com.unitrade.entity.ListingReport;
import com.unitrade.entity.ListingStatus;
import com.unitrade.entity.Product;
import com.unitrade.entity.ReportStatus;
import com.unitrade.entity.User;
import com.unitrade.exception.ApiException;
import com.unitrade.repository.ListingReportRepository;
import com.unitrade.repository.ProductRepository;
import com.unitrade.repository.UserRepository;
import com.unitrade.security.CurrentUserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Listing reports (filed by students) and admin moderation of reports and user accounts.
 * Admin URLs are restricted to ROLE_ADMIN by Spring Security; the admin role is re-checked here
 * against the database as defence in depth.
 */
@Service
public class ModerationService {

    private final ListingReportRepository reportRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductService productService;
    private final CurrentUserService currentUserService;
    private final DtoMapper mapper;

    public ModerationService(ListingReportRepository reportRepository, ProductRepository productRepository,
                             UserRepository userRepository, ProductService productService,
                             CurrentUserService currentUserService, DtoMapper mapper) {
        this.reportRepository = reportRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.productService = productService;
        this.currentUserService = currentUserService;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------- students

    @Transactional
    public ReportReceiptDto report(Long productId, CreateReportRequest request) {
        User reporter = currentUserService.requireUser();
        Product product = productService.findOrThrow(productId);
        if (product.getStatus() == ListingStatus.HIDDEN) {
            throw ApiException.notFound("Listing not found.");
        }
        if (product.isOwnedBy(reporter)) {
            throw ApiException.badRequest("You cannot report your own listing.");
        }
        if (reportRepository.existsByProductIdAndReporterId(productId, reporter.getId())) {
            throw ApiException.conflict("You have already reported this listing. Our moderators will review it.");
        }
        ListingReport report = new ListingReport();
        report.setProduct(product);
        report.setReporter(reporter);
        report.setReason(request.reason());
        report.setDetails(TextSanitizer.blankToNull(request.details()));
        report.setStatus(ReportStatus.PENDING);
        try {
            reportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("You have already reported this listing. Our moderators will review it.");
        }
        return new ReportReceiptDto(report.getId(), report.getStatus(), report.getCreatedAt());
    }

    // ------------------------------------------------------------- admins

    @Transactional(readOnly = true)
    public List<ReportDto> reports(ReportStatus status) {
        currentUserService.requireAdmin();
        return reportRepository.findAllWithDetails(status).stream().map(mapper::toDto).toList();
    }

    @Transactional
    public ReportDto resolve(Long reportId, ResolveReportRequest request) {
        User admin = currentUserService.requireAdmin();
        if (request.status() == ReportStatus.PENDING) {
            throw ApiException.badRequest("Choose ACTION_TAKEN or DISMISSED to resolve a report.");
        }
        ListingReport report = reportRepository.findWithDetails(reportId)
                .orElseThrow(() -> ApiException.notFound("Report not found."));
        report.setStatus(request.status());
        report.setAdminNote(TextSanitizer.blankToNull(request.adminNote()));
        report.setResolvedBy(admin);
        report.setResolvedAt(LocalDateTime.now());
        if (request.hideListing()) {
            report.getProduct().setStatus(ListingStatus.HIDDEN);
        }
        reportRepository.saveAndFlush(report);
        return mapper.toDto(report);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserDto> users(String query, int page, int size) {
        currentUserService.requireAdmin();
        String q = TextSanitizer.blankToNull(query);
        Page<User> result = userRepository.search(q,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageResponse<>(
                result.getContent().stream().map(this::toAdminDto).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    /** Suspends or reinstates a student. Suspended users cannot log in and existing sessions lose access. */
    @Transactional
    public AdminUserDto setUserEnabled(Long userId, boolean enabled) {
        User admin = currentUserService.requireAdmin();
        User target = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User not found."));
        if (target.getId().equals(admin.getId())) {
            throw ApiException.badRequest("You cannot suspend your own account.");
        }
        if (target.isAdmin()) {
            throw ApiException.forbidden("Administrator accounts cannot be suspended from the dashboard.");
        }
        target.setEnabled(enabled);
        userRepository.saveAndFlush(target);
        return toAdminDto(target);
    }

    @Transactional(readOnly = true)
    public AdminStatsDto stats() {
        currentUserService.requireAdmin();
        return new AdminStatsDto(
                userRepository.count(),
                productRepository.countByStatus(ListingStatus.AVAILABLE),
                productRepository.countByStatus(ListingStatus.SOLD),
                productRepository.countByStatus(ListingStatus.HIDDEN),
                reportRepository.countByStatus(ReportStatus.PENDING));
    }

    private AdminUserDto toAdminDto(User u) {
        return new AdminUserDto(u.getId(), u.getFullName(), u.getEmail(), u.getCampus(), u.getRole(),
                u.isEnabled(), productRepository.countBySellerId(u.getId()), u.getCreatedAt());
    }
}
