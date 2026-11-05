package com.unitrade;

import com.fasterxml.jackson.databind.JsonNode;
import com.unitrade.entity.Role;
import com.unitrade.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTestBase {

    private Map<String, Object> registration(String email) {
        Map<String, Object> body = new HashMap<>();
        body.put("fullName", "Priya Nair");
        body.put("email", email);
        body.put("password", "secret123");
        body.put("campus", CAMPUS_A);
        body.put("phone", "9876543210");
        return body;
    }

    @Test
    void registerCreatesStudentWithHashedPasswordAndNoSecretsInResponse() throws Exception {
        String email = unique("priya") + "@example.com";
        Map<String, Object> body = registration(email);
        body.put("role", "ADMIN"); // attempt privilege escalation: must be ignored

        MvcResult result = mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("secret123");

        User saved = userRepository.findByEmail(email).orElseThrow();
        assertThat(saved.getRole()).isEqualTo(Role.STUDENT);
        assertThat(saved.getPasswordHash()).startsWith("$2").isNotEqualTo("secret123");
        assertThat(passwordEncoder.matches("secret123", saved.getPasswordHash())).isTrue();
    }

    @Test
    void registerRejectsDuplicateEmailCaseInsensitively() throws Exception {
        String email = unique("dup") + "@example.com";
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(registration(email))))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(registration(email.toUpperCase()))))
                .andExpect(status().isConflict());
    }

    @Test
    void registerValidatesInput() throws Exception {
        Map<String, Object> body = registration("not-an-email");
        body.put("password", "short");
        body.put("campus", "Unknown Campus");
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void registerWithUnknownCampusIsRejected() throws Exception {
        Map<String, Object> body = registration(unique("campus") + "@example.com");
        body.put("campus", "Hogwarts");
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void stateChangingRequestsWithoutCsrfTokenAreRejected() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(json(registration(unique("c") + "@example.com"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void csrfEndpointReturnsToken() throws Exception {
        mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void loginCreatesSessionThatAuthenticatesLaterRequests() throws Exception {
        User user = createStudent("Arjun Verma");

        MvcResult login = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mvc.perform(get("/api/auth/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));

        // Logout invalidates the session.
        mvc.perform(post("/api/auth/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized() throws Exception {
        User user = createStudent("Meera Joshi");
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.getEmail(), "password", "wrong-password1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void suspendedUserCannotLogIn() throws Exception {
        User user = createStudent("Suspended Student");
        user.setEnabled(false);
        userRepository.saveAndFlush(user);
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.getEmail(), "password", PASSWORD))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousAccessToPrivateEndpointsIsUnauthorized() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/wishlist")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me/products")).andExpect(status().isUnauthorized());
        MvcResult session = mvc.perform(get("/api/auth/session")).andExpect(status().isOk()).andReturn();
        JsonNode node = body(session);
        assertThat(node.get("authenticated").asBoolean()).isFalse();
    }

    @Test
    void publicStaticPagesAreServed() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk());
        mvc.perform(get("/marketplace.html")).andExpect(status().isOk());
        mvc.perform(get("/js/api.js")).andExpect(status().isOk());
    }
}
