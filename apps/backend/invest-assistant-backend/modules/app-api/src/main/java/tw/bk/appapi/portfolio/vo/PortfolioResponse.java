package tw.bk.appapi.portfolio.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.apppersistence.entity.PortfolioEntity;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioResponse {

    private String id;
    private String name;
    private String baseCurrency;

    public static PortfolioResponse from(PortfolioEntity entity) {
        return PortfolioResponse.builder()
                .id(String.valueOf(entity.getId()))
                .name(entity.getName())
                .baseCurrency(entity.getBaseCurrency())
                .build();
    }
}
