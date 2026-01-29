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
import tw.bk.appauth.service.UserSettingsService;
import tw.bk.appcommon.error.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;
import tw.bk.apppersistence.entity.UserSettingsEntity;

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
        Long userId = requireUserId();
        UserSettingsEntity settings = userSettingsService.update(
                userId,
                request.getBaseCurrency(),
                request.getDisplayTimezone());
        return Result.ok(UserSettingsResponse.from(settings));
    }

    private Long requireUserId() {
        return currentUserProvider.getUserId()
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized"));
    }
}
