package tw.bk.appapi.stocks.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 交易所回應
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeResponse {

    @JsonProperty("code")
    private String code;

    @JsonProperty("name")
    private String name;

    @JsonProperty("market")
    private String market;

    public static ExchangeResponse of(String code, String name, String market) {
        return ExchangeResponse.builder()
                .code(code)
                .name(name)
                .market(market)
                .build();
    }
}
