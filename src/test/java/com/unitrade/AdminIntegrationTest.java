package com.unitrade;

import com.unitrade.entity.Category;
import com.unitrade.entity.ListingStatus;
import com.unitrade.entity.Product;
import com.unitrade.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminIntegrationTest extends IntegrationTestBase {

    @Test
    void adminEndpointsRequireAdminRole() throws Exception {
        User student = createStudent("Regular Student");
        User admin = createAdmin();

        for (String url : new String[]{"/api/admin/reports", "/api/admin/stats", "/api/admin/users", "/api/admin/products"}) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
            mvc.perform(get(url).with(as(student))).andExpect(status().isForbidden());
            mvc.perform(get(url).with(as(admin))).andExpect(status().isOk());
        }

        Product p = createProduct(student, "Some listing", Category.OTHER, "100", CAMPUS_A);
        mvc.perform(patch("/api/admin/products/{id}/status", p.getId()).with(as(student)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "HIDDEN"))))
                .andExpect(status().isForbidden());
        assertThat(productRepository.findById(p.getId()).orElseThrow().getStatus()).isEqualTo(ListingStatus.AVAILABLE);
    }

    @Test
    void reportAndModerationFlow() throws Exception {
        User seller = createStudent("Shady Seller");
        User reporter = createStudent("Careful Reporter");
        User admin = createAdmin();
        Product p = createProduct(seller, "Too good to be true laptop", Category.ELECTRONICS, "500", CAMPUS_A);

        MvcResult created = mvc.perform(post("/api/products/{id}/reports", p.getId()).with(as(reporter)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "SCAM", "details", "Asked me to pay in advance."))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        long reportId = body(created).get("id").asLong();

        // Duplicate reports from the same student are rejected
        mvc.perform(post("/api/products/{id}/reports", p.getId()).with(as(reporter)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "SPAM"))))
                .andExpect(status().isConflict());
        // Sellers cannot report their own listing
        mvc.perform(post("/api/products/{id}/reports", p.getId()).with(as(seller)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "SPAM"))))
                .andExpect(status().isBadRequest());

        // Students cannot resolve reports
        mvc.perform(patch("/api/admin/reports/{id}", reportId).with(as(reporter)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "DISMISSED", "hideListing", false))))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/reports").param("status", "PENDING").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + reportId + ")].reason").value("SCAM"));

        mvc.perform(patch("/api/admin/reports/{id}", reportId).with(as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "ACTION_TAKEN", "adminNote", "Confirmed scam", "hideListing", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTION_TAKEN"))
                .andExpect(jsonPath("$.productStatus").value("HIDDEN"));

        assertThat(productRepository.findById(p.getId()).orElseThrow().getStatus()).isEqualTo(ListingStatus.HIDDEN);
        mvc.perform(get("/api/products/{id}", p.getId())).andExpect(status().isNotFound());

        // The seller cannot un-hide a moderated listing
        mvc.perform(patch("/api/products/{id}/status", p.getId()).with(as(seller)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "AVAILABLE"))))
                .andExpect(status().isForbidden());

        // Admin can restore it
        mvc.perform(patch("/api/admin/products/{id}/status", p.getId()).with(as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "AVAILABLE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void adminCanSuspendStudentsButNotAdmins() throws Exception {
        User student = createStudent("To Be Suspended");
        User admin = createAdmin();
        User otherAdmin = createAdmin();

        mvc.perform(patch("/api/admin/users/{id}/status", student.getId()).with(as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("enabled", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // The suspended student's existing session no longer grants access
        mvc.perform(get("/api/wishlist").with(as(student))).andExpect(status().isForbidden());

        mvc.perform(patch("/api/admin/users/{id}/status", otherAdmin.getId()).with(as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("enabled", false))))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/users/{id}/status", admin.getId()).with(as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("enabled", false))))
                .andExpect(status().isBadRequest());

        // Reinstate
        mvc.perform(patch("/api/admin/users/{id}/status", student.getId()).with(as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("enabled", true))))
                .andExpect(jsonPath("$.enabled").value(true));
        mvc.perform(get("/api/wishlist").with(as(student))).andExpect(status().isOk());
    }

    @Test
    void securityContextRoleIsNotEnoughIfDatabaseSaysStudent() throws Exception {
        // A forged session claiming ADMIN for a STUDENT account passes the URL rule but fails the DB re-check.
        User student = createStudent("Forged Admin");
        mvc.perform(get("/api/admin/stats").with(org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.user(new com.unitrade.security.UserPrincipal(
                                student.getId(), student.getEmail(), null, com.unitrade.entity.Role.ADMIN, true))))
                .andExpect(status().isForbidden());
    }
}
