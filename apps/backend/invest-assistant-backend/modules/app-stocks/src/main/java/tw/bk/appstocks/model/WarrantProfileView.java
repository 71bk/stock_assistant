package tw.bk.appstocks.model;

import java.time.LocalDate;

public record WarrantProfileView(
        String underlyingSymbol,
        LocalDate expiryDate) {
}
