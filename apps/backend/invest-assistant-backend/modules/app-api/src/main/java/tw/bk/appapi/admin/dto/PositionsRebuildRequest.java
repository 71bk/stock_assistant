package tw.bk.appapi.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PositionsRebuildRequest {
    @NotNull
    private Long portfolioId;
    private Long instrumentId;
}
