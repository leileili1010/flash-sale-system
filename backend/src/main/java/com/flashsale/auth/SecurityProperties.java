package com.flashsale.auth;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flashsale.security")
public record SecurityProperties(Jwt jwt, Cors cors) {

    static final int MIN_SECRET_BYTES = 32;

    /**
     * @param issuer value of the {@code iss} claim, verified on every request
     * @param secret Base64-encoded HMAC-SHA256 key
     * @param ttl    lifetime of issued access tokens
     */
    public record Jwt(String issuer, String secret, Duration ttl) {

        public Jwt {
            if (secretBytes(secret).length < MIN_SECRET_BYTES) {
                throw new IllegalArgumentException(
                        "flashsale.security.jwt.secret must decode to at least " + MIN_SECRET_BYTES + " bytes");
            }
        }

        byte[] secretBytes() {
            return secretBytes(secret);
        }

        private static byte[] secretBytes(String secret) {
            return Base64.getDecoder().decode(secret);
        }
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
