package com.unitrade.repository;

import com.unitrade.entity.Inquiry;
import com.unitrade.entity.InquiryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    @Query("""
            select i from Inquiry i
            join fetch i.product
            join fetch i.buyer
            join fetch i.seller
            where i.buyer.id = :userId
            order by i.lastMessageAt desc
            """)
    List<Inquiry> findSentBy(@Param("userId") Long userId);

    @Query("""
            select i from Inquiry i
            join fetch i.product
            join fetch i.buyer
            join fetch i.seller
            where i.seller.id = :userId
            order by i.lastMessageAt desc
            """)
    List<Inquiry> findReceivedBy(@Param("userId") Long userId);

    @Query("""
            select i from Inquiry i
            join fetch i.product
            join fetch i.buyer
            join fetch i.seller
            where i.id = :id
            """)
    Optional<Inquiry> findWithParticipants(@Param("id") Long id);

    Optional<Inquiry> findFirstByProductIdAndBuyerIdAndStatus(Long productId, Long buyerId, InquiryStatus status);
}
