package com.unitrade.controller;

import com.unitrade.dto.ProductDtos.ProductSummaryDto;
import com.unitrade.service.WishlistService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @GetMapping
    public List<ProductSummaryDto> list() {
        return wishlistService.list();
    }

    /** IDs only, used by the marketplace grid to show filled hearts. */
    @GetMapping("/ids")
    public List<Long> ids() {
        return wishlistService.productIds();
    }

    /** 201 when added, 200 when it was already in the wishlist. */
    @PostMapping("/{productId}")
    public ResponseEntity<Map<String, Object>> add(@PathVariable Long productId) {
        boolean added = wishlistService.add(productId);
        return ResponseEntity.status(added ? HttpStatus.CREATED : HttpStatus.OK)
                .body(Map.of("productId", productId, "wishlisted", true));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> remove(@PathVariable Long productId) {
        wishlistService.remove(productId);
        return ResponseEntity.noContent().build();
    }
}
