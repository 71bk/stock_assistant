package tw.bk.appapi.users;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.bk.appapi.users.dto.UpdateUserSettingsRequest;
import tw.bk.appapi.users.vo.UserSettingsResponse;
import tw.bk.appauth.model.UserSettingsView;
import tw.bk.appauth.service.UserSettingsService;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;
import tw.bk.appapi.web.CurrentUser;

@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "User settings")
public class UserSettingsController {
    private final UserSettingsService userSettingsService;
    private final CurrentUserProvider currentUserProvider;

    public UserSettingsController(UserSettingsService userSettingsService,
            CurrentUserProvider currentUserProvider) {
        this.userSettingsService = userSettingsService;
        this.currentUserProvider = currentUserProvider;
    }

    @PatchMapping("/me/settings")
    @Operation(summary = "Update user settings")
    public Result<UserSettingsResponse> updateSettings(
            @Valid @RequestBody UpdateUserSettingsRequest request) {
        Long userId = CurrentUser.require(currentUserProvider);
        UserSettingsView settings = userSettingsService.update(
                userId,
                request.getBaseCurrency(),
                request.getDisplayTimezone());
        return Result.ok(UserSettingsResponse.from(settings));
    }
}
