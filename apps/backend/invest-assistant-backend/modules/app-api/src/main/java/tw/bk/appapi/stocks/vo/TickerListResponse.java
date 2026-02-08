package tw.bk.appapi.stocks.vo;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appcommon.enums.TickerType;
import tw.bk.appcommon.util.TimeFormatUtils;
import tw.bk.appstocks.model.TickerList;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TickerListResponse {
    private String date;
    private TickerType type;
    private String exchange;
    private String market;
    private String industry;
    private Boolean isNormal;
    private Boolean isAttention;
    private Boolean isDisposition;
    private Boolean isHalted;
    private List<TickerItemResponse> data;

    public static TickerListResponse from(TickerList list) {
        if (list == null) {
            return null;
        }
        List<TickerItemResponse> items = list.getData() == null
                ? Collections.emptyList()
                : list.getData().stream()
                .map(TickerItemResponse::from)
                .collect(Collectors.toList());

        return TickerListResponse.builder()
                .date(TimeFormatUtils.formatDate(list.getDate()))
                .type(TickerType.from(list.getType()))
                .exchange(list.getExchange())
                .market(list.getMarket())
                .industry(list.getIndustry())
                .isNormal(list.getIsNormal())
                .isAttention(list.getIsAttention())
                .isDisposition(list.getIsDisposition())
                .isHalted(list.getIsHalted())
                .data(items)
                .build();
    }
}
