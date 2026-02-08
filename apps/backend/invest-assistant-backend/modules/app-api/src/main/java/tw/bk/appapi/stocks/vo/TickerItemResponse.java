package tw.bk.appapi.stocks.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appstocks.model.TickerItem;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TickerItemResponse {
    private String symbol;
    private String name;

    public static TickerItemResponse from(TickerItem item) {
        if (item == null) {
            return null;
        }
        return TickerItemResponse.builder()
                .symbol(item.getSymbol())
                .name(item.getName())
                .build();
    }
}
