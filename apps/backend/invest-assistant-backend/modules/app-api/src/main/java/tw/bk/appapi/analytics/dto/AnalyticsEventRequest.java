package tw.bk.appapi.analytics.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AnalyticsEventRequest {
    @NotNull
    private UUID eventId;

    @NotNull
    private UUID sessionId;

    @NotBlank
    private String eventType;

    @NotBlank
    @Size(max = 200)
    private String route;

    @NotNull
    private OffsetDateTime occurredAt;
}
