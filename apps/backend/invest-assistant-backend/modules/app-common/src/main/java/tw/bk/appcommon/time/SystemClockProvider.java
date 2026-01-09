package tw.bk.appcommon.time;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * 系統時鐘提供者
 */
@Component
public final class SystemClockProvider implements ClockProvider {
    private final Clock clock;

    public SystemClockProvider() {
        this(Clock.systemUTC());
    }

    public SystemClockProvider(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Instant now() {
        return clock.instant();
    }
}
