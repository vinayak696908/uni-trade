package com.unitrade.repository;

import com.unitrade.entity.ListingReport;
import com.unitrade.entity.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ListingReportRepository extends JpaRepository<ListingReport, Long> {

    boolean existsByProductIdAndReporterId(Long productId, Long reporterId);

    long countByStatus(ReportStatus status);

    @Query("""
            select r from ListingReport r
            join fetch r.product p
            join fetch p.seller
            join fetch r.reporter
            left join fetch r.resolvedBy
            where (:status is null or r.status = :status)
            order by r.createdAt desc
            """)
    List<ListingReport> findAllWithDetails(@Param("status") ReportStatus status);

    @Query("""
            select r from ListingReport r
            join fetch r.product p
            join fetch p.seller
            join fetch r.reporter
            where r.id = :id
            """)
    Optional<ListingReport> findWithDetails(@Param("id") Long id);
}
