package tw.bk.appapi.portfolio.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appportfolio.model.PortfolioValuationView;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioValuationResponse {
    private LocalDate date;
    private BigDecimal totalValue;
    private BigDecimal cashValue;
    private BigDecimal positionsValue;
    private String currency;

    public static PortfolioValuationResponse from(PortfolioValuationView entity) {
        return PortfolioValuationResponse.builder()
                .date(entity.asOfDate())
                .totalValue(entity.totalValue())
                .cashValue(entity.cashValue())
                .positionsValue(entity.positionsValue())
                .currency(entity.baseCurrency())
                .build();
    }
}
