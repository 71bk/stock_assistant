package tw.bk.appstocks.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TickerQuery {
    private String type;
    private String exchange;
    private String market;
    private String industry;
    private Boolean isNormal;
    private Boolean isAttention;
    private Boolean isDisposition;
    private Boolean isHalted;
}
