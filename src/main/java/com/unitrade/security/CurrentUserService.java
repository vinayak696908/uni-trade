package com.unitrade.security;

import com.unitrade.entity.User;
import com.unitrade.exception.ApiException;
import com.unitrade.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Resolves the logged-in user from the security context and reloads it from the database on every
 * call, so suspensions and role changes take effect immediately even for existing sessions.
 */
@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<Long> currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UserPrincipal principal) {
            return Optional.of(principal.getId());
        }
        return Optional.empty();
    }

    /** Returns the current user if logged in and active; empty for anonymous visitors. */
    public Optional<User> optionalUser() {
        return currentUserId()
                .flatMap(userRepository::findById)
                .filter(User::isEnabled);
    }

    /** Returns the current user or fails with 401 (not logged in) / 403 (suspended). */
    public User requireUser() {
        Long id = currentUserId().orElseThrow(() -> ApiException.unauthorized("Please log in to continue."));
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.unauthorized("Your session is no longer valid. Please log in again."));
        if (!user.isEnabled()) {
            throw ApiException.forbidden("Your account has been suspended. Contact the moderators.");
        }
        return user;
    }

    public User requireAdmin() {
        User user = requireUser();
        if (!user.isAdmin()) {
            throw ApiException.forbidden("Administrator access is required.");
        }
        return user;
    }
}
