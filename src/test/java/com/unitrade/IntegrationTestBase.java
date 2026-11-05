package com.unitrade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unitrade.entity.Campuses;
import com.unitrade.entity.Category;
import com.unitrade.entity.ItemCondition;
import com.unitrade.entity.ListingStatus;
import com.unitrade.entity.Product;
import com.unitrade.entity.Role;
import com.unitrade.entity.User;
import com.unitrade.repository.ProductRepository;
import com.unitrade.repository.UserRepository;
import com.unitrade.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * Boots the full application against MySQL (profile "test"). Every test method runs in a transaction
 * that is rolled back afterwards.
 */
@SpringBootTest(properties = "app.seed.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class IntegrationTestBase {

    protected static final String CAMPUS_A = Campuses.ALL.get(0);
    protected static final String CAMPUS_B = Campuses.ALL.get(1);
    protected static final String PASSWORD = "Passw0rd!";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected ProductRepository productRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    protected User createUser(String name, Role role) {
        User u = new User();
        u.setFullName(name);
        u.setEmail(unique("user") + "@example.com");
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setCampus(CAMPUS_A);
        u.setRole(role);
        u.setEnabled(true);
        return userRepository.saveAndFlush(u);
    }

    protected User createStudent(String name) {
        return createUser(name, Role.STUDENT);
    }

    protected User createAdmin() {
        return createUser("Test Admin", Role.ADMIN);
    }

    protected Product createProduct(User seller, String title, Category category, String price, String campus) {
        Product p = new Product();
        p.setSeller(seller);
        p.setTitle(title);
        p.setDescription("Test description for " + title);
        p.setCategory(category);
        p.setCondition(ItemCondition.GOOD);
        p.setPrice(new BigDecimal(price));
        p.setCampus(campus);
        p.setStatus(ListingStatus.AVAILABLE);
        return productRepository.saveAndFlush(p);
    }

    /** Authenticates a MockMvc request as the given user (same principal type the real login stores in the session). */
    protected RequestPostProcessor as(User u) {
        return user(new UserPrincipal(u.getId(), u.getEmail(), null, u.getRole(), u.isEnabled()));
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected Map<String, Object> productPayload(String title, String category, String price, String campus) {
        return Map.of(
                "title", title,
                "description", "A perfectly good item for students: " + title,
                "category", category,
                "condition", "GOOD",
                "price", price,
                "campus", campus);
    }
}
