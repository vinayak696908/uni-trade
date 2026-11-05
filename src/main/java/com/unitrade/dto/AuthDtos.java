package com.unitrade.dto;

import com.unitrade.entity.Role;
import com.unitrade.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Request/response payloads for authentication and the user's own profile.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "Full name is required")
            @Size(min = 2, max = 100, message = "Full name must be 2-100 characters")
            String fullName,

            @NotBlank(message = "Email is required")
            @Email(message = "Enter a valid email address")
            @Size(max = 191, message = "Email is too long")
            String email,

            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must contain at least one letter and one number")
            String password,

            @NotBlank(message = "Campus is required")
            String campus,

            @Pattern(regexp = "^$|^[+]?[0-9 ]{10,15}$", message = "Enter a valid phone number (10-15 digits)")
            String phone
    ) {
    }

    public record LoginRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Enter a valid email address")
            String email,

            @NotBlank(message = "Password is required")
            @Size(max = 72, message = "Password is too long")
            String password
    ) {
    }

    public record UpdateProfileRequest(
            @NotBlank(message = "Full name is required")
            @Size(min = 2, max = 100, message = "Full name must be 2-100 characters")
            String fullName,

            @NotBlank(message = "Campus is required")
            String campus,

            @Pattern(regexp = "^$|^[+]?[0-9 ]{10,15}$", message = "Enter a valid phone number (10-15 digits)")
            String phone
    ) {
    }

    /** The authenticated user's own profile. Contains private fields, so it is only returned to that user. */
    public record UserProfileDto(
            Long id,
            String fullName,
            String displayName,
            String email,
            String campus,
            String phone,
            Role role,
            LocalDateTime createdAt
    ) {
        public static UserProfileDto from(User u) {
            return new UserProfileDto(u.getId(), u.getFullName(), u.getDisplayName(), u.getEmail(),
                    u.getCampus(), u.getPhone(), u.getRole(), u.getCreatedAt());
        }
    }

    public record CsrfTokenDto(String headerName, String token) {
    }
}
