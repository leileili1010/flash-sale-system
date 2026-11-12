package com.flashsale.common.time;

import java.time.Clock;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Returns the authoritative server time. Clients use it to correct for local clock skew so
 * drop countdowns finish at the same moment the server starts accepting purchases.
 */
@RestController
@RequestMapping("/api/time")
public class TimeController {

    private final Clock clock;

    public TimeController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping
    public ServerTime now() {
        Instant now = clock.instant();
        return new ServerTime(now, now.toEpochMilli());
    }

    public record ServerTime(Instant serverTime, long epochMillis) {
    }
}
