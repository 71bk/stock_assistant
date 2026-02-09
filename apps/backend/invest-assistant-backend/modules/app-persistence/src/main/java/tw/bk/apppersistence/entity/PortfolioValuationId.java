package tw.bk.apppersistence.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class PortfolioValuationId implements Serializable {
    private Long portfolioId;
    private LocalDate asOfDate;

    public PortfolioValuationId() {
    }

    public PortfolioValuationId(Long portfolioId, LocalDate asOfDate) {
        this.portfolioId = portfolioId;
        this.asOfDate = asOfDate;
    }

    public Long getPortfolioId() {
        return portfolioId;
    }

    public void setPortfolioId(Long portfolioId) {
        this.portfolioId = portfolioId;
    }

    public LocalDate getAsOfDate() {
        return asOfDate;
    }

    public void setAsOfDate(LocalDate asOfDate) {
        this.asOfDate = asOfDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PortfolioValuationId that = (PortfolioValuationId) o;
        return Objects.equals(portfolioId, that.portfolioId)
                && Objects.equals(asOfDate, that.asOfDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(portfolioId, asOfDate);
    }
}
