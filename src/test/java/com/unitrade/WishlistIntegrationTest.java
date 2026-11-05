package com.unitrade;

import com.unitrade.entity.Category;
import com.unitrade.entity.ListingStatus;
import com.unitrade.entity.Product;
import com.unitrade.entity.User;
import com.unitrade.repository.WishlistRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WishlistIntegrationTest extends IntegrationTestBase {

    @Autowired
    private WishlistRepository wishlistRepository;

    @Test
    void addListAndRemoveWishlistItems() throws Exception {
        User seller = createStudent("Wish Seller");
        User buyer = createStudent("Wish Buyer");
        Product p = createProduct(seller, "Engineering drawing set", Category.STATIONERY, "600", CAMPUS_A);

        mvc.perform(post("/api/wishlist/{id}", p.getId()).with(as(buyer)).with(csrf()))
                .andExpect(status().isCreated());
        assertThat(wishlistRepository.existsByUserIdAndProductId(buyer.getId(), p.getId())).isTrue();

        // Adding twice is idempotent
        mvc.perform(post("/api/wishlist/{id}", p.getId()).with(as(buyer)).with(csrf()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/wishlist").with(as(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(p.getId()));
        mvc.perform(get("/api/wishlist/ids").with(as(buyer)))
                .andExpect(jsonPath("$[0]").value(p.getId()));

        // Product detail shows the wishlisted flag for this user only
        mvc.perform(get("/api/products/{id}", p.getId()).with(as(buyer)))
                .andExpect(jsonPath("$.wishlisted").value(true));
        mvc.perform(get("/api/products/{id}", p.getId()).with(as(seller)))
                .andExpect(jsonPath("$.wishlisted").value(false));

        // Another user's wishlist is separate
        mvc.perform(get("/api/wishlist").with(as(createStudent("Third Person"))))
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(delete("/api/wishlist/{id}", p.getId()).with(as(buyer)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(wishlistRepository.existsByUserIdAndProductId(buyer.getId(), p.getId())).isFalse();

        mvc.perform(delete("/api/wishlist/{id}", p.getId()).with(as(buyer)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotWishlistOwnOrHiddenOrMissingListing() throws Exception {
        User seller = createStudent("Self Saver");
        Product own = createProduct(seller, "My own item", Category.OTHER, "100", CAMPUS_A);
        mvc.perform(post("/api/wishlist/{id}", own.getId()).with(as(seller)).with(csrf()))
                .andExpect(status().isBadRequest());

        Product hidden = createProduct(seller, "Hidden item", Category.OTHER, "100", CAMPUS_A);
        hidden.setStatus(ListingStatus.HIDDEN);
        productRepository.saveAndFlush(hidden);
        User buyer = createStudent("Hidden Buyer");
        mvc.perform(post("/api/wishlist/{id}", hidden.getId()).with(as(buyer)).with(csrf()))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/wishlist/{id}", 987654321L).with(as(buyer)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void wishlistRequiresLogin() throws Exception {
        mvc.perform(get("/api/wishlist")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/wishlist/{id}", 1L).with(csrf())).andExpect(status().isUnauthorized());
    }
}
