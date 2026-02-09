package tw.bk.appapi.users.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appauth.model.UserSettingsView;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSettingsResponse {
    private String baseCurrency;
    private String displayTimezone;

    public static UserSettingsResponse from(UserSettingsView entity) {
        return UserSettingsResponse.builder()
                .baseCurrency(entity.baseCurrency())
                .displayTimezone(entity.displayTimezone())
                .build();
    }
}
