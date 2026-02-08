package tw.bk.appapi.users.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserSettingsRequest {
    @Size(min = 3, max = 3, message = "baseCurrency must be exactly 3 characters")
    private String baseCurrency;

    @Size(max = 100, message = "displayTimezone must be less than 100 characters")
    private String displayTimezone;
}
