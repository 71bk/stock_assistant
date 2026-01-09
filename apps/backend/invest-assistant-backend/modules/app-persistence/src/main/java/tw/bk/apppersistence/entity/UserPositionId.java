package tw.bk.apppersistence.entity;

import java.io.Serializable;
import java.util.Objects;

public class UserPositionId implements Serializable {
    private Long portfolioId;
    private Long instrumentId;

    public UserPositionId() {
    }

    public UserPositionId(Long portfolioId, Long instrumentId) {
        this.portfolioId = portfolioId;
        this.instrumentId = instrumentId;
    }

    public Long getPortfolioId() {
        return portfolioId;
    }

    public void setPortfolioId(Long portfolioId) {
        this.portfolioId = portfolioId;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UserPositionId that = (UserPositionId) o;
        return Objects.equals(portfolioId, that.portfolioId)
                && Objects.equals(instrumentId, that.instrumentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(portfolioId, instrumentId);
    }
}
