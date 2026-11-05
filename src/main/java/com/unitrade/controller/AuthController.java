package com.unitrade.controller;

import com.unitrade.dto.AuthDtos.CsrfTokenDto;
import com.unitrade.dto.AuthDtos.LoginRequest;
import com.unitrade.dto.AuthDtos.RegisterRequest;
import com.unitrade.dto.AuthDtos.UserProfileDto;
import com.unitrade.exception.ApiException;
import com.unitrade.security.CurrentUserService;
import com.unitrade.security.LoginAttemptService;
import com.unitrade.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Session-based authentication.
 * Logout is handled by Spring Security's LogoutFilter at POST /api/auth/logout (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AccountService accountService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final LoginAttemptService loginAttemptService;
    private final CurrentUserService currentUserService;

    public AuthController(AccountService accountService, AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository, CsrfTokenRepository csrfTokenRepository,
                          LoginAttemptService loginAttemptService, CurrentUserService currentUserService) {
        this.accountService = accountService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.loginAttemptService = loginAttemptService;
        this.currentUserService = currentUserService;
    }

    /** Returns the CSRF token the browser must send in the given header on POST/PUT/PATCH/DELETE. */
    @GetMapping("/csrf")
    public CsrfTokenDto csrf(CsrfToken token) {
        return new CsrfTokenDto(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserProfileDto register(@Valid @RequestBody RegisterRequest request) {
        return accountService.register(request);
    }

    @PostMapping("/login")
    public UserProfileDto login(@Valid @RequestBody LoginRequest request,
                                HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String email = AccountService.normalizeEmail(request.email());
        if (loginAttemptService.isBlocked(email)) {
            throw ApiException.tooManyRequests("Too many failed attempts. Please wait 15 minutes and try again.");
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        } catch (DisabledException e) {
            throw ApiException.forbidden("Your account has been suspended. Contact the moderators.");
        } catch (AuthenticationException e) {
            loginAttemptService.recordFailure(email);
            throw ApiException.unauthorized("Invalid email or password.");
        }
        loginAttemptService.recordSuccess(email);

        // Session fixation protection: rotate the session id if a pre-login session exists.
        if (httpRequest.getSession(false) != null) {
            httpRequest.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
        // Rotate the CSRF token; the frontend fetches a fresh one after login.
        csrfTokenRepository.saveToken(null, httpRequest, httpResponse);

        return accountService.currentProfile();
    }

    @GetMapping("/me")
    public UserProfileDto me() {
        return accountService.currentProfile();
    }

    /**
     * Always 200: lets pages render the navigation for both anonymous and logged-in visitors
     * without producing 401 noise in the browser console.
     */
    @GetMapping("/session")
    public Map<String, Object> session() {
        Map<String, Object> body = new LinkedHashMap<>();
        boolean authenticated = currentUserService.optionalUser().isPresent();
        body.put("authenticated", authenticated);
        body.put("user", authenticated ? accountService.currentProfile() : null);
        return body;
    }
}
