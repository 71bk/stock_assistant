package tw.bk.appapi.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.bk.appai.service.AiConversationService;
import tw.bk.appapi.admin.config.AdminProperties;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;

@RestController
@RequestMapping("/admin/ai")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin operations")
public class AdminAiConversationController {
    private static final String ADMIN_HEADER = "X-Admin-Key";

    private final AiConversationService conversationService;
    private final AdminProperties adminProperties;
    private final CurrentUserProvider currentUserProvider;

    @DeleteMapping("/conversations/{conversationId}")
    @Operation(summary = "Hard delete a conversation")
    public Result<Void> hardDeleteConversation(
            @RequestHeader(value = ADMIN_HEADER, required = false) String adminKey,
            @PathVariable Long conversationId) {
        requireAdminKey(adminKey);
        conversationService.hardDeleteConversationAsAdmin(conversationId);
        return Result.ok();
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
