package tw.bk.appapi.admin;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.bk.appai.service.AiConversationService;
import tw.bk.appapi.admin.security.AdminKeyGuard;
import tw.bk.appcommon.result.Result;

@ExtendWith(MockitoExtension.class)
class AdminAiConversationControllerTest {

    @Mock
    private AiConversationService conversationService;

    @Mock
    private AdminKeyGuard adminKeyGuard;

    private AdminAiConversationController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminAiConversationController(conversationService, adminKeyGuard);
    }

    @Test
    void hardDeleteConversation_shouldInvokeService() {
        Result<Void> result = controller.hardDeleteConversation(null, 88L);

        assertTrue(result.isSuccess());
        verify(conversationService).hardDeleteConversationAsAdmin(88L);
    }
}
