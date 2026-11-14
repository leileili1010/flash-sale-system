package com.flashsale.benchmark;

import com.flashsale.auth.TokenService;
import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;
import com.flashsale.user.User;
import com.flashsale.user.UserRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Load-testing helpers, registered only with the {@code benchmark} profile. Never enable it in a
 * shared environment: these endpoints are unauthenticated by design.
 */
@Profile("benchmark")
@RestController
@RequestMapping("/api/demo")
class BenchmarkController {

    static final int MAX_USERS_PER_CALL = 1_000;

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final TokenService tokens;
    private final Clock clock;
    private final String sharedPasswordHash;

    BenchmarkController(JdbcTemplate jdbc, UserRepository users, TokenService tokens, PasswordEncoder encoder, Clock clock) {
        this.jdbc = jdbc;
        this.users = users;
        this.tokens = tokens;
        this.clock = clock;
        // One hash for every load-test account: hashing thousands of passwords would dominate setup time.
        this.sharedPasswordHash = encoder.encode("load-test-password");
    }

    /**
     * Creates {@code count} shopper accounts named {@code <run>-<n>@loadtest.local} and returns a JWT
     * for each, so a load test can authenticate thousands of virtual users without logging in.
     */
    @PostMapping("/users")
    @Transactional
    Map<String, List<String>> createUsers(@RequestParam String run, @RequestParam int count, @RequestParam(defaultValue = "0") int offset) {
        if (count < 1 || count > MAX_USERS_PER_CALL || !run.matches("[a-z0-9-]{1,40}")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "count must be 1-" + MAX_USERS_PER_CALL + " and run [a-z0-9-]");
        }
        Timestamp now = Timestamp.from(clock.instant());
        List<String> emails = IntStream.range(offset, offset + count).mapToObj(i -> run + "-" + i + "@loadtest.local").toList();
        jdbc.batchUpdate("""
                INSERT INTO users (email, password_hash, display_name, role, created_at)
                VALUES (?, ?, ?, 'USER', ?) ON CONFLICT DO NOTHING
                """, emails, 500, (ps, email) -> {
            ps.setString(1, email);
            ps.setString(2, sharedPasswordHash);
            ps.setString(3, "Load Tester");
            ps.setTimestamp(4, now);
        });
        List<String> issued = emails.stream()
                .map(email -> users.findByEmail(email).orElseThrow())
                .map((User user) -> tokens.issue(user).value())
                .toList();
        return Map.of("tokens", issued);
    }

    /**
     * The reference project's original approach, kept for comparison: read the stock, check it in
     * application code, then write the decremented value back. Under concurrency, many requests
     * read the same value and all "succeed", so far more purchases are accepted than units exist.
     * Each accepted purchase is recorded so the oversell can be measured afterwards.
     */
    @PostMapping("/naive-purchase/{dropId}")
    Map<String, String> naivePurchase(@PathVariable long dropId) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT available_stock, sold_stock FROM drops WHERE id = ?", dropId);
        int available = (Integer) row.get("available_stock");
        int sold = (Integer) row.get("sold_stock");
        if (available <= 0) {
            throw new ApiException(ErrorCode.SOLD_OUT);
        }
        // Absolute write of values computed from a possibly stale read: the classic lost update.
        jdbc.update("UPDATE drops SET available_stock = ?, sold_stock = ? WHERE id = ?", available - 1, sold + 1, dropId);
        jdbc.update("INSERT INTO naive_purchases (drop_id) VALUES (?)", dropId);
        return Map.of("result", "accepted");
    }

    /** How many naive purchases were accepted for a drop, versus how many units it had. */
    @GetMapping("/naive-purchase/{dropId}")
    Map<String, Object> naiveResult(@PathVariable long dropId) {
        Integer accepted = jdbc.queryForObject("SELECT count(*) FROM naive_purchases WHERE drop_id = ?", Integer.class, dropId);
        Integer total = jdbc.queryForObject("SELECT total_stock FROM drops WHERE id = ?", Integer.class, dropId);
        return Map.of("dropId", dropId, "totalStock", total, "acceptedPurchases", accepted, "oversold", Math.max(0, accepted - total));
    }
}
