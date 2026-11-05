package com.unitrade;

import com.unitrade.entity.Category;
import com.unitrade.entity.Product;
import com.unitrade.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InquiryIntegrationTest extends IntegrationTestBase {

    private long startInquiry(User buyer, Product p, String message) throws Exception {
        MvcResult result = mvc.perform(post("/api/inquiries").with(as(buyer)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("productId", p.getId(), "message", message))))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("id").asLong();
    }

    @Test
    void buyerAndSellerCanConverseAndOthersCannotAccess() throws Exception {
        User seller = createStudent("Seller Sharma");
        User buyer = createStudent("Buyer Bose");
        User stranger = createStudent("Stranger Das");
        User admin = createAdmin();
        Product p = createProduct(seller, "Arduino starter kit", Category.PROJECT_COMPONENTS, "1400", CAMPUS_A);

        long id = startInquiry(buyer, p, "Is the kit complete?");

        // Buyer sees it under "sent", seller under "received"
        mvc.perform(get("/api/inquiries/sent").with(as(buyer)))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].myRole").value("BUYER"));
        mvc.perform(get("/api/inquiries/received").with(as(seller)))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].myRole").value("SELLER"))
                .andExpect(jsonPath("$[0].counterpartName").value("Buyer B."));
        mvc.perform(get("/api/inquiries/received").with(as(stranger)))
                .andExpect(jsonPath("$.length()").value(0));

        // Non-participants (including admins) cannot read or write the conversation
        mvc.perform(get("/api/inquiries/{id}", id).with(as(stranger))).andExpect(status().isNotFound());
        mvc.perform(get("/api/inquiries/{id}", id).with(as(admin))).andExpect(status().isNotFound());
        mvc.perform(post("/api/inquiries/{id}/messages", id).with(as(stranger)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("message", "spam"))))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/inquiries/{id}/close", id).with(as(stranger)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/inquiries/{id}", id)).andExpect(status().isUnauthorized());

        // Seller replies
        mvc.perform(post("/api/inquiries/{id}/messages", id).with(as(seller)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("message", "Yes, all parts included."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[1].mine").value(true));

        // Buyer reads the thread
        mvc.perform(get("/api/inquiries/{id}", id).with(as(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].body").value("Is the kit complete?"))
                .andExpect(jsonPath("$.messages[0].mine").value(true))
                .andExpect(jsonPath("$.messages[1].mine").value(false));

        // A second inquiry from the same buyer is appended to the open thread
        long again = startInquiry(buyer, p, "Can we meet tomorrow?");
        org.assertj.core.api.Assertions.assertThat(again).isEqualTo(id);

        // Either participant can close; no further replies
        mvc.perform(patch("/api/inquiries/{id}/close", id).with(as(buyer)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        mvc.perform(post("/api/inquiries/{id}/messages", id).with(as(seller)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("message", "Still there?"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sellerCannotInquireAboutOwnListing() throws Exception {
        User seller = createStudent("Self Asker");
        Product p = createProduct(seller, "Lab coat", Category.LAB_EQUIPMENT, "250", CAMPUS_A);
        mvc.perform(post("/api/inquiries").with(as(seller)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("productId", p.getId(), "message", "Hello me"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inquiryValidation() throws Exception {
        User buyer = createStudent("Validator");
        mvc.perform(post("/api/inquiries").with(as(buyer)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("productId", 1L, "message", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.message").exists());
    }
}
