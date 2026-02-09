package tw.bk.appapi.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.bk.appapi.admin.config.AdminProperties;
import tw.bk.appapi.admin.dto.PositionsRebuildRequest;
import tw.bk.appapi.admin.vo.PositionsRebuildResponse;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;
import tw.bk.appportfolio.model.PortfolioPositionsRebuildResult;
import tw.bk.appportfolio.service.PortfolioService;

@RestController
@RequestMapping("/admin/portfolios")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin operations")
public class AdminPortfolioController {
    private static final String ADMIN_HEADER = "X-Admin-Key";

    private final PortfolioService portfolioService;
    private final AdminProperties adminProperties;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/positions-rebuild")
    @Operation(summary = "Rebuild positions for a portfolio")
    public Result<PositionsRebuildResponse> rebuildPositions(
            @RequestHeader(value = ADMIN_HEADER, required = false) String adminKey,
            @Valid @RequestBody PositionsRebuildRequest request) {
        requireAdminKey(adminKey);
        PortfolioPositionsRebuildResult result = portfolioService.rebuildPositions(
                request.getPortfolioId(),
                request.getInstrumentId());
        return Result.ok(PositionsRebuildResponse.from(result));
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
