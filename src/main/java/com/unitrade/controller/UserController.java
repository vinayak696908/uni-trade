package com.unitrade.controller;

import com.unitrade.dto.AuthDtos.UpdateProfileRequest;
import com.unitrade.dto.AuthDtos.UserProfileDto;
import com.unitrade.dto.ProductDtos.ProductSummaryDto;
import com.unitrade.service.AccountService;
import com.unitrade.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/me")
public class UserController {

    private final AccountService accountService;
    private final ProductService productService;

    public UserController(AccountService accountService, ProductService productService) {
        this.accountService = accountService;
        this.productService = productService;
    }

    @GetMapping
    public UserProfileDto profile() {
        return accountService.currentProfile();
    }

    @PutMapping
    public UserProfileDto updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return accountService.updateProfile(request);
    }

    /** All of the current user's listings, including SOLD and HIDDEN ones. */
    @GetMapping("/products")
    public List<ProductSummaryDto> myListings() {
        return productService.myListings();
    }
}
