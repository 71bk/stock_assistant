package tw.bk.appapi.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.bk.appapi.admin.dto.PortfolioSnapshotRequest;
import tw.bk.appapi.admin.security.AdminKeyGuard;
import tw.bk.appapi.admin.vo.PortfolioSnapshotResponse;
import tw.bk.appapi.rag.PortfolioSnapshotService;
import tw.bk.appcommon.result.Result;

@RestController
@RequestMapping("/admin/rag")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin operations")
public class AdminRagController {
    private final PortfolioSnapshotService snapshotService;
    private final AdminKeyGuard adminKeyGuard;

    @PostMapping("/portfolio-snapshots")
    @Operation(summary = "Trigger portfolio snapshot ingestion")
    public Result<PortfolioSnapshotResponse> snapshotPortfolios(
            HttpServletRequest request,
            @RequestBody(required = false) PortfolioSnapshotRequest body) {
        adminKeyGuard.require(request);
        Long userId = body != null ? body.getUserId() : null;
        Long portfolioId = body != null ? body.getPortfolioId() : null;

        PortfolioSnapshotService.SnapshotResult result = snapshotService.runSnapshots(userId, portfolioId);
        return Result.ok(PortfolioSnapshotResponse.builder()
                .total(result.total())
                .ingested(result.ingested())
                .skipped(result.skipped())
                .failed(result.failed())
                .build());
    }
}
