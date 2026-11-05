package com.unitrade.repository;

import com.unitrade.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    boolean existsByStoredFilename(String storedFilename);
}
