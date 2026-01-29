package tw.bk.appstocks.model;

import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TickerList {
    private LocalDate date;
    private String type;
    private String exchange;
    private String market;
    private String industry;
    private Boolean isNormal;
    private Boolean isAttention;
    private Boolean isDisposition;
    private Boolean isHalted;
    private List<TickerItem> data;
}
