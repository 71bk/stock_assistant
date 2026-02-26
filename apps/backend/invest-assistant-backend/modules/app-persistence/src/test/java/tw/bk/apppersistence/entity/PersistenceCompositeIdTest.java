package tw.bk.apppersistence.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PersistenceCompositeIdTest {

    @Test
    void portfolioValuationId_shouldImplementStableEqualsAndHashCode() {
        PortfolioValuationId left = new PortfolioValuationId(100L, LocalDate.parse("2026-02-25"));
        PortfolioValuationId right = new PortfolioValuationId(100L, LocalDate.parse("2026-02-25"));
        PortfolioValuationId different = new PortfolioValuationId(100L, LocalDate.parse("2026-02-24"));

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertNotEquals(left, different);
    }

    @Test
    void userPositionId_shouldImplementStableEqualsAndHashCode() {
        UserPositionId left = new UserPositionId(200L, 300L);
        UserPositionId right = new UserPositionId(200L, 300L);
        UserPositionId different = new UserPositionId(200L, 301L);

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertNotEquals(left, different);
    }
}
