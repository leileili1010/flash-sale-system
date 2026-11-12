package com.flashsale.demo;

import com.flashsale.user.Role;
import com.flashsale.user.User;
import com.flashsale.user.UserRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates well-known demo accounts for local development. Never enable in a shared environment. */
@Component
@ConditionalOnBooleanProperty("flashsale.demo.seed-users")
public class DemoUserSeeder implements ApplicationRunner {

    public static final String ADMIN_EMAIL = "admin@fluffle.dev";
    public static final String ADMIN_PASSWORD = "admin-password";
    public static final String SHOPPER_EMAIL = "shopper@fluffle.dev";
    public static final String SHOPPER_PASSWORD = "shopper-password";

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DemoUserSeeder(UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createIfMissing(ADMIN_EMAIL, ADMIN_PASSWORD, "Demo Admin", Role.ADMIN);
        createIfMissing(SHOPPER_EMAIL, SHOPPER_PASSWORD, "Demo Shopper", Role.USER);
    }

    private void createIfMissing(String email, String password, String displayName, Role role) {
        if (!users.existsByEmail(email)) {
            users.save(new User(email, passwordEncoder.encode(password), displayName, role, clock.instant()));
            log.info("Created demo {} account {}", role, email);
        }
    }
}
