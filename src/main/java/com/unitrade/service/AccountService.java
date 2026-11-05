package com.unitrade.service;

import com.unitrade.dto.AuthDtos.RegisterRequest;
import com.unitrade.dto.AuthDtos.UpdateProfileRequest;
import com.unitrade.dto.AuthDtos.UserProfileDto;
import com.unitrade.entity.Campuses;
import com.unitrade.entity.Role;
import com.unitrade.entity.User;
import com.unitrade.exception.ApiException;
import com.unitrade.repository.UserRepository;
import com.unitrade.security.CurrentUserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;

    public AccountService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          CurrentUserService currentUserService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUserService = currentUserService;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public UserProfileDto register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (!Campuses.isValid(request.campus())) {
            throw ApiException.badRequest("Please choose a campus from the list.");
        }
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("An account with this email already exists.");
        }
        User user = new User();
        user.setFullName(TextSanitizer.singleLine(request.fullName()));
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setCampus(request.campus());
        user.setPhone(TextSanitizer.blankToNull(request.phone()));
        user.setRole(Role.STUDENT); // Self-registration can never create an admin.
        user.setEnabled(true);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Race between existsByEmail and insert: the unique index is the real guard.
            throw ApiException.conflict("An account with this email already exists.");
        }
        return UserProfileDto.from(user);
    }

    @Transactional(readOnly = true)
    public UserProfileDto currentProfile() {
        return UserProfileDto.from(currentUserService.requireUser());
    }

    @Transactional
    public UserProfileDto updateProfile(UpdateProfileRequest request) {
        User user = currentUserService.requireUser();
        if (!Campuses.isValid(request.campus())) {
            throw ApiException.badRequest("Please choose a campus from the list.");
        }
        user.setFullName(TextSanitizer.singleLine(request.fullName()));
        user.setCampus(request.campus());
        user.setPhone(TextSanitizer.blankToNull(request.phone()));
        return UserProfileDto.from(userRepository.save(user));
    }
}
