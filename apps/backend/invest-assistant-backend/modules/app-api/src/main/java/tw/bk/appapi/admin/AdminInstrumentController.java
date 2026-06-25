package tw.bk.appapi.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.bk.appapi.admin.security.AdminKeyGuard;
import tw.bk.appapi.admin.vo.InstrumentSyncResponse;
import tw.bk.appcommon.result.Result;
import tw.bk.appstocks.service.InstrumentSyncService;

@RestController
@RequestMapping("/admin/instruments")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin operations")
public class AdminInstrumentController {
    private final InstrumentSyncService instrumentSyncService;
    private final AdminKeyGuard adminKeyGuard;

    @PostMapping("/sync")
    @Operation(summary = "Sync TW instruments (EQUITY only)")
    public Result<InstrumentSyncResponse> sync(HttpServletRequest request) {
        adminKeyGuard.require(request);
        InstrumentSyncService.SyncResult result = instrumentSyncService.syncTwEquityInstruments();
        return Result.ok(InstrumentSyncResponse.builder()
                .added(result.added())
                .skipped(result.skipped())
                .build());
    }

    @PostMapping("/sync-warrants")
    @Operation(summary = "Sync TW Warrants from TWSE and TPEx")
    public Result<InstrumentSyncResponse> syncWarrants(HttpServletRequest request) {
        adminKeyGuard.require(request);
        InstrumentSyncService.SyncResult result = instrumentSyncService.syncTwWarrantInstruments();
        return Result.ok(InstrumentSyncResponse.builder()
                .added(result.added())
                .skipped(result.skipped())
                .build());
    }
}
