package tw.bk.appapi.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.bk.appapi.admin.config.AdminProperties;
import tw.bk.appapi.admin.dto.PortfolioSnapshotRequest;
import tw.bk.appapi.admin.vo.PortfolioSnapshotResponse;
import tw.bk.appapi.rag.PortfolioSnapshotService;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;

@RestController
@RequestMapping("/admin/rag")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin operations")
public class AdminRagController {
    private static final String ADMIN_HEADER = "X-Admin-Key";

    private final PortfolioSnapshotService snapshotService;
    private final AdminProperties adminProperties;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/portfolio-snapshots")
    @Operation(summary = "Trigger portfolio snapshot ingestion")
    public Result<PortfolioSnapshotResponse> snapshotPortfolios(
            @RequestHeader(value = ADMIN_HEADER, required = false) String adminKey,
            @RequestBody(required = false) PortfolioSnapshotRequest request) {
        requireAdminKey(adminKey);
        Long userId = request != null ? request.getUserId() : null;
        Long portfolioId = request != null ? request.getPortfolioId() : null;

        PortfolioSnapshotService.SnapshotResult result = snapshotService.runSnapshots(userId, portfolioId);
        return Result.ok(PortfolioSnapshotResponse.builder()
                .total(result.total())
                .ingested(result.ingested())
                .skipped(result.skipped())
                .failed(result.failed())
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
