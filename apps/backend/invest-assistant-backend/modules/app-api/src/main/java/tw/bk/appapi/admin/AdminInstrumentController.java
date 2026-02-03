package tw.bk.appapi.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.bk.appapi.admin.config.AdminProperties;
import tw.bk.appapi.admin.vo.InstrumentSyncResponse;
import tw.bk.appcommon.error.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;
import tw.bk.appstocks.service.InstrumentSyncService;

@RestController
@RequestMapping("/admin/instruments")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin operations")
public class AdminInstrumentController {
    private static final String ADMIN_HEADER = "X-Admin-Key";

    private final InstrumentSyncService instrumentSyncService;
    private final AdminProperties adminProperties;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/sync")
    @Operation(summary = "Sync TW instruments (EQUITY only)")
    public Result<InstrumentSyncResponse> sync(
            @RequestHeader(value = ADMIN_HEADER, required = false) String adminKey) {
        requireAdminKey(adminKey);
        InstrumentSyncService.SyncResult result = instrumentSyncService.syncTwEquityInstruments();
        return Result.ok(InstrumentSyncResponse.builder()
                .added(result.added())
                .skipped(result.skipped())
                .build());
    }

    private void requireAdminKey(String provided) {
        String expected = adminProperties.getApiKey();
        if (expected == null || expected.isBlank()) {
            if (currentUserProvider.getUserId().isEmpty()) {
                throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
            }
            return;
        }
        if (provided == null || provided.isBlank() || !expected.equals(provided)) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "Admin key invalid");
        }
    }
}
