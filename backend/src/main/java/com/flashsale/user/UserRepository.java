package com.flashsale.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Expects an already-normalized email; see {@link User#normalizeEmail(String)}. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
