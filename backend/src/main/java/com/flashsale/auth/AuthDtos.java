package com.flashsale.auth;

import com.flashsale.user.Role;
import com.flashsale.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72, message = "must be between 8 and 72 characters") String password,
        @NotBlank @Size(max = 100) String displayName) {
}

record LoginRequest(@NotBlank String email, @NotBlank String password) {
}

record UserView(long id, String email, String displayName, Role role) {

    static UserView from(User user) {
        return new UserView(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole());
    }
}

record AuthResponse(String accessToken, String tokenType, Instant expiresAt, UserView user) {

    static AuthResponse of(User user, TokenService.IssuedToken token) {
        return new AuthResponse(token.value(), "Bearer", token.expiresAt(), UserView.from(user));
    }
}
