package tw.bk.appapi.analytics.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AnalyticsEventBatchRequest {
    @Valid
    @NotEmpty
    @Size(max = 20)
    private List<AnalyticsEventRequest> events;
}
