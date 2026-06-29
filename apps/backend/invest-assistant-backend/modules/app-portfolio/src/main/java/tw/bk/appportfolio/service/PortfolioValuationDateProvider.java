package tw.bk.appportfolio.service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tw.bk.appcommon.time.ClockProvider;

/** Resolves the portfolio valuation date from the configured business time zone. */
@Component
class PortfolioValuationDateProvider {
    private static final Logger log = LoggerFactory.getLogger(PortfolioValuationDateProvider.class);

    private final ClockProvider clockProvider;

    @Value("${app.portfolio.valuation.zone:Asia/Taipei}")
    private String valuationZone = "Asia/Taipei";

    PortfolioValuationDateProvider(ClockProvider clockProvider) {
        this.clockProvider = clockProvider;
    }

    LocalDate currentDate() {
        try {
            String zoneName = PortfolioAmounts.isBlank(valuationZone) ? "UTC" : valuationZone.trim();
            return clockProvider.now().atZone(ZoneId.of(zoneName)).toLocalDate();
        } catch (DateTimeException ex) {
            log.warn("Invalid valuation zone '{}', fallback to UTC", valuationZone);
            return clockProvider.nowUtc().toLocalDate();
        }
    }
}
