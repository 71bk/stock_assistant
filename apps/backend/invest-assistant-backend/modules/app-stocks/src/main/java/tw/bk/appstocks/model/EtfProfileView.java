package tw.bk.appstocks.model;

import java.time.LocalDate;

public record EtfProfileView(
        String underlyingType,
        String underlyingName,
        LocalDate asOfDate) {
}
