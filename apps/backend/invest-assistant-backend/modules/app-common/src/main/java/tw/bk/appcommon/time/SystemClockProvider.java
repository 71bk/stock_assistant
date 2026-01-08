package tw.bk.appcommon.time;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

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
