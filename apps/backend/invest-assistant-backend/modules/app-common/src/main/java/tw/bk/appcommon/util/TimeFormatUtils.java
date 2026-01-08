package tw.bk.appcommon.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class TimeFormatUtils {
    private static final DateTimeFormatter ISO_INSTANT = DateTimeFormatter.ISO_INSTANT;
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private TimeFormatUtils() {
    }

    public static String formatInstant(Instant instant) {
        return instant == null ? null : ISO_INSTANT.format(instant);
    }

    public static String formatDate(LocalDate date) {
        return date == null ? null : ISO_DATE.format(date);
    }

    public static Instant parseInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    public static LocalDate parseDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value, ISO_DATE);
    }

    public static OffsetDateTime toUtc(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC);
    }
}
