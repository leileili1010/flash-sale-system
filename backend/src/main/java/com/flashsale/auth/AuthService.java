package com.flashsale.auth;

import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;
import com.flashsale.user.Role;
import com.flashsale.user.User;
import com.flashsale.user.UserRepository;
import java.time.Clock;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final Clock clock;
    // Compared against when the email is unknown, so response time does not reveal which emails exist.
    private final String dummyHash;

    AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("timing-equalization-placeholder");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = User.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_TAKEN);
        }
        User user;
        try {
            user = users.saveAndFlush(new User(email, passwordEncoder.encode(request.password()),
                    request.displayName(), Role.USER, clock.instant()));
        } catch (DataIntegrityViolationException raceWithConcurrentSignup) {
            throw new ApiException(ErrorCode.EMAIL_TAKEN);
        }
        return AuthResponse.of(user, tokens.issue(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Optional<User> user = users.findByEmail(User.normalizeEmail(request.email()));
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !matches) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        return AuthResponse.of(user.get(), tokens.issue(user.get()));
    }

    @Transactional(readOnly = true)
    public UserView currentUser(long userId) {
        return users.findById(userId)
                .map(UserView::from)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }
}
