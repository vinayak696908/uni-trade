package com.unitrade;

import com.fasterxml.jackson.databind.JsonNode;
import com.unitrade.entity.Category;
import com.unitrade.entity.ListingStatus;
import com.unitrade.entity.Product;
import com.unitrade.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductIntegrationTest extends IntegrationTestBase {

    // ------------------------------------------------------------ create & read

    @Test
    void studentCanCreateListingAndAnyoneCanReadIt() throws Exception {
        User seller = createStudent("Kabir Singh");
        String title = unique("Scientific calculator ");

        MvcResult created = mvc.perform(post("/api/products").with(as(seller)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productPayload(title, "CALCULATORS", "899.50", CAMPUS_A))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.owner").value(true))
                .andReturn();
        long id = body(created).get("id").asLong();

        Product persisted = productRepository.findById(id).orElseThrow();
        assertThat(persisted.getSeller().getId()).isEqualTo(seller.getId());
        assertThat(persisted.getPrice()).isEqualByComparingTo("899.50");

        // Anonymous visitors can view it, but seller contact details are not exposed.
        MvcResult publicView = mvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.owner").value(false))
                .andExpect(jsonPath("$.seller.displayName").value("Kabir S."))
                .andExpect(jsonPath("$.seller.email").doesNotExist())
                .andExpect(jsonPath("$.seller.phone").doesNotExist())
                .andReturn();
        assertThat(publicView.getResponse().getContentAsString()).doesNotContain(seller.getEmail());

        mvc.perform(get("/api/products").param("q", title))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));
    }

    @Test
    void anonymousUserCannotCreateListing() throws Exception {
        mvc.perform(post("/api/products").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productPayload("Lab coat", "LAB_EQUIPMENT", "200", CAMPUS_A))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidListingIsRejected() throws Exception {
        User seller = createStudent("Validation Tester");
        Map<String, Object> payload = new HashMap<>(productPayload("ok title", "BOOKS", "0", CAMPUS_A));
        payload.put("title", "");
        mvc.perform(post("/api/products").with(as(seller)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.price").exists());

        payload = new HashMap<>(productPayload("Valid title", "NOT_A_CATEGORY", "100", CAMPUS_A));
        mvc.perform(post("/api/products").with(as(seller)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownListingReturns404() throws Exception {
        mvc.perform(get("/api/products/{id}", 999999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ------------------------------------------------------------ update / delete permissions

    @Test
    void onlyOwnerOrAdminCanUpdateListing() throws Exception {
        User owner = createStudent("Owner One");
        User other = createStudent("Other Student");
        User admin = createAdmin();
        Product p = createProduct(owner, "Original title", Category.BOOKS, "300", CAMPUS_A);

        String update = json(productPayload("Hacked title", "BOOKS", "1", CAMPUS_A));
        mvc.perform(put("/api/products/{id}", p.getId()).with(as(other)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isForbidden());
        assertThat(productRepository.findById(p.getId()).orElseThrow().getTitle()).isEqualTo("Original title");

        mvc.perform(put("/api/products/{id}", p.getId()).with(as(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productPayload("Updated by owner", "BOOKS", "350", CAMPUS_B))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated by owner"))
                .andExpect(jsonPath("$.campus").value(CAMPUS_B));

        mvc.perform(put("/api/products/{id}", p.getId()).with(as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productPayload("Updated by admin", "BOOKS", "350", CAMPUS_B))))
                .andExpect(status().isOk());

        Product reloaded = productRepository.findById(p.getId()).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Updated by admin");
        assertThat(reloaded.getSeller().getId()).isEqualTo(owner.getId());
    }

    @Test
    void onlyOwnerOrAdminCanDeleteListing() throws Exception {
        User owner = createStudent("Owner Two");
        User other = createStudent("Intruder");
        Product p = createProduct(owner, "Breadboard kit", Category.PROJECT_COMPONENTS, "150", CAMPUS_A);

        mvc.perform(delete("/api/products/{id}", p.getId()).with(as(other)).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(productRepository.existsById(p.getId())).isTrue();

        mvc.perform(delete("/api/products/{id}", p.getId()).with(as(owner)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(productRepository.existsById(p.getId())).isFalse();
        mvc.perform(get("/api/products/{id}", p.getId())).andExpect(status().isNotFound());

        Product second = createProduct(owner, "Drawing kit", Category.STATIONERY, "500", CAMPUS_A);
        mvc.perform(delete("/api/admin/products/{id}", second.getId()).with(as(createAdmin())).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(productRepository.existsById(second.getId())).isFalse();
    }

    @Test
    void ownerCanMarkListingSoldAndItLeavesPublicSearch() throws Exception {
        User owner = createStudent("Seller Sold");
        String token = unique("soldtoken");
        Product p = createProduct(owner, "Arduino " + token, Category.PROJECT_COMPONENTS, "1200", CAMPUS_A);

        mvc.perform(patch("/api/products/{id}/status", p.getId()).with(as(createStudent("Not Owner"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "SOLD"))))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/products/{id}/status", p.getId()).with(as(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "SOLD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLD"));

        mvc.perform(get("/api/products").param("q", token))
                .andExpect(jsonPath("$.totalElements").value(0));

        // Sellers cannot hide their own listing (that is a moderator action).
        mvc.perform(patch("/api/products/{id}/status", p.getId()).with(as(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "HIDDEN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void hiddenListingIsVisibleOnlyToOwnerAndAdmin() throws Exception {
        User owner = createStudent("Hidden Owner");
        Product p = createProduct(owner, "Hidden item", Category.OTHER, "100", CAMPUS_A);
        p.setStatus(ListingStatus.HIDDEN);
        productRepository.saveAndFlush(p);

        mvc.perform(get("/api/products/{id}", p.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/api/products/{id}", p.getId()).with(as(createStudent("Someone Else")))).andExpect(status().isNotFound());
        mvc.perform(get("/api/products/{id}", p.getId()).with(as(owner))).andExpect(status().isOk());
        mvc.perform(get("/api/products/{id}", p.getId()).with(as(createAdmin()))).andExpect(status().isOk());
    }

    // ------------------------------------------------------------ search & filtering

    @Test
    void searchFiltersAndSortsListings() throws Exception {
        User seller = createStudent("Search Seller");
        String token = unique("srch");
        createProduct(seller, "DSA textbook " + token, Category.BOOKS, "400", CAMPUS_A);
        createProduct(seller, "Java textbook " + token, Category.BOOKS, "250", CAMPUS_B);
        createProduct(seller, "Calculator " + token, Category.CALCULATORS, "900", CAMPUS_A);
        Product sold = createProduct(seller, "Sold book " + token, Category.BOOKS, "100", CAMPUS_A);
        sold.setStatus(ListingStatus.SOLD);
        productRepository.saveAndFlush(sold);

        // Text search (title + description), only AVAILABLE listings
        mvc.perform(get("/api/products").param("q", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));

        // Multiple terms must all match
        mvc.perform(get("/api/products").param("q", "textbook " + token))
                .andExpect(jsonPath("$.totalElements").value(2));

        // Category filter
        mvc.perform(get("/api/products").param("q", token).param("category", "CALCULATORS"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].category").value("CALCULATORS"));

        // Campus filter
        mvc.perform(get("/api/products").param("q", token).param("campus", CAMPUS_B))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].campus").value(CAMPUS_B));

        // Condition filter (all fixtures are GOOD)
        mvc.perform(get("/api/products").param("q", token).param("condition", "NEW"))
                .andExpect(jsonPath("$.totalElements").value(0));

        // Price range
        mvc.perform(get("/api/products").param("q", token).param("minPrice", "300").param("maxPrice", "500"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].price").value(400.0));

        // Sorting by price
        MvcResult asc = mvc.perform(get("/api/products").param("q", token).param("sort", "price_asc"))
                .andExpect(status().isOk()).andReturn();
        JsonNode content = body(asc).get("content");
        assertThat(content.get(0).get("price").asDouble()).isEqualTo(250.0);
        assertThat(content.get(2).get("price").asDouble()).isEqualTo(900.0);

        MvcResult desc = mvc.perform(get("/api/products").param("q", token).param("sort", "price_desc")).andReturn();
        assertThat(body(desc).get("content").get(0).get("price").asDouble()).isEqualTo(900.0);

        // Invalid range and invalid enum are client errors
        mvc.perform(get("/api/products").param("minPrice", "500").param("maxPrice", "100"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("category", "WEAPONS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchTreatsWildcardCharactersLiterally() throws Exception {
        User seller = createStudent("Wildcard Seller");
        String token = unique("wc");
        createProduct(seller, "Plain item " + token, Category.OTHER, "50", CAMPUS_A);
        mvc.perform(get("/api/products").param("q", token + "%"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ------------------------------------------------------------ images

    private static byte[] pngBytes() throws Exception {
        BufferedImage img = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void ownerCanUploadValidImageAndItIsServed() throws Exception {
        User owner = createStudent("Image Owner");
        Product p = createProduct(owner, "Lab coat with photo", Category.LAB_EQUIPMENT, "250", CAMPUS_A);
        MockMultipartFile file = new MockMultipartFile("images", "../../evil name.png", "image/png", pngBytes());

        MvcResult result = mvc.perform(multipart("/api/products/{id}/images", p.getId()).file(file).with(as(owner)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images.length()").value(1))
                .andReturn();
        String url = body(result).get("images").get(0).get("url").asText();
        assertThat(url).matches("/api/images/[a-f0-9]{32}\\.png");

        mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void uploadRejectsNonImagesAndOtherUsers() throws Exception {
        User owner = createStudent("Upload Owner");
        Product p = createProduct(owner, "Calculator", Category.CALCULATORS, "700", CAMPUS_A);

        MockMultipartFile fake = new MockMultipartFile("images", "shell.png", "image/png",
                "<?php system($_GET['c']); ?>".getBytes());
        mvc.perform(multipart("/api/products/{id}/images", p.getId()).file(fake).with(as(owner)).with(csrf()))
                .andExpect(status().isBadRequest());

        MockMultipartFile html = new MockMultipartFile("images", "page.html", "text/html", "<script>alert(1)</script>".getBytes());
        mvc.perform(multipart("/api/products/{id}/images", p.getId()).file(html).with(as(owner)).with(csrf()))
                .andExpect(status().isBadRequest());

        MockMultipartFile real = new MockMultipartFile("images", "ok.png", "image/png", pngBytes());
        mvc.perform(multipart("/api/products/{id}/images", p.getId()).file(real).with(as(createStudent("Not Owner"))).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void imageEndpointRejectsTraversalNames() throws Exception {
        // Encoded traversal is rejected either by Spring Security's firewall (400) or by the name check (404).
        mvc.perform(get("/api/images/{name}", "..%2F..%2Fapplication.properties")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/images/{name}", "..")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/images/{name}", "not-a-valid-name.jpg")).andExpect(status().isNotFound());
    }

    @Test
    void myListingsReturnsOnlyOwnListings() throws Exception {
        User me = createStudent("Me Myself");
        User other = createStudent("Someone Else");
        Product mine = createProduct(me, "Mine", Category.BOOKS, "100", CAMPUS_A);
        createProduct(other, "Theirs", Category.BOOKS, "100", CAMPUS_A);

        mvc.perform(get("/api/users/me/products").with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(mine.getId()));
    }
}
