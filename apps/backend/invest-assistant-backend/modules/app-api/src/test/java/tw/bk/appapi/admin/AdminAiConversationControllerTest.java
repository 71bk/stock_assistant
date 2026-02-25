package tw.bk.appapi.admin;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.bk.appai.service.AiConversationService;
import tw.bk.appapi.admin.config.AdminProperties;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
class AdminAiConversationControllerTest {

    @Mock
    private AiConversationService conversationService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private AdminProperties adminProperties;
    private AdminAiConversationController controller;

    @BeforeEach
    void setUp() {
        adminProperties = new AdminProperties();
        adminProperties.setApiKey("");
        controller = new AdminAiConversationController(conversationService, adminProperties, currentUserProvider);
    }

    @Test
    void hardDeleteConversation_shouldInvokeService() {
        when(currentUserProvider.getUserId()).thenReturn(Optional.of(1L));

        Result<Void> result = controller.hardDeleteConversation(null, 88L);

        assertTrue(result.isSuccess());
        verify(conversationService).hardDeleteConversationAsAdmin(88L);
    }
}
