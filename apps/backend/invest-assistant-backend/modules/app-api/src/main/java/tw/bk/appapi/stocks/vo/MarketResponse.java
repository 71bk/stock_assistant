package tw.bk.appapi.stocks.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 市場回應
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketResponse {

    @JsonProperty("code")
    private String code;

    @JsonProperty("name")
    private String name;

    public static MarketResponse of(String code, String name) {
        return MarketResponse.builder()
                .code(code)
                .name(name)
                .build();
    }
}
