package com.unitrade.repository;

import com.unitrade.entity.ListingStatus;
import com.unitrade.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WishlistRepository extends JpaRepository<WishlistItem, Long> {

    Optional<WishlistItem> findByUserIdAndProductId(Long userId, Long productId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    @Query("""
            select w from WishlistItem w
            join fetch w.product p
            join fetch p.seller
            where w.user.id = :userId and p.status <> :hidden
            order by w.createdAt desc
            """)
    List<WishlistItem> findVisibleForUser(@Param("userId") Long userId, @Param("hidden") ListingStatus hidden);

    @Query("select w.product.id from WishlistItem w where w.user.id = :userId")
    List<Long> findProductIdsByUserId(@Param("userId") Long userId);
}
