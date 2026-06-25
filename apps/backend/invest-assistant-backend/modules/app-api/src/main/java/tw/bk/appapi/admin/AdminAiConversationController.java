package tw.bk.appapi.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.bk.appai.service.AiConversationService;
import tw.bk.appapi.admin.security.AdminKeyGuard;
import tw.bk.appcommon.result.Result;

@RestController
@RequestMapping("/admin/ai")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin operations")
public class AdminAiConversationController {
    private final AiConversationService conversationService;
    private final AdminKeyGuard adminKeyGuard;

    @DeleteMapping("/conversations/{conversationId}")
    @Operation(summary = "Hard delete a conversation")
    public Result<Void> hardDeleteConversation(
            HttpServletRequest request,
            @PathVariable Long conversationId) {
        adminKeyGuard.require(request);
        conversationService.hardDeleteConversationAsAdmin(conversationId);
        return Result.ok();
    }
}
