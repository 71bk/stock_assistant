package tw.bk.appapi.users.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.apppersistence.entity.UserSettingsEntity;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSettingsResponse {
    private String baseCurrency;
    private String displayTimezone;

    public static UserSettingsResponse from(UserSettingsEntity entity) {
        return UserSettingsResponse.builder()
                .baseCurrency(entity.getBaseCurrency())
                .displayTimezone(entity.getDisplayTimezone())
                .build();
    }
}
