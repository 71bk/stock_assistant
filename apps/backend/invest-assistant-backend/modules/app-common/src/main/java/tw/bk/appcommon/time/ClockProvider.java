package tw.bk.appcommon.time;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public interface ClockProvider {
    Instant now();

    default OffsetDateTime nowUtc() {
        return OffsetDateTime.ofInstant(now(), ZoneOffset.UTC);
    }
}
