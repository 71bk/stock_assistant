package tw.bk.appapi.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import tw.bk.appapi.rag.dto.RagQueryRequest;
import tw.bk.appapi.rag.vo.RagDocumentResponse;
import tw.bk.appapi.rag.vo.RagQueryResponse;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.result.PageResponse;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;
import tw.bk.apprag.client.AiWorkerQueryResponse;
import tw.bk.apprag.client.AiWorkerRagClient;
import tw.bk.apprag.model.RagDocumentView;
import tw.bk.apprag.service.RagDocumentService;
import tw.bk.appfiles.service.FileService;

@ExtendWith(MockitoExtension.class)
class RagControllerTest {

    @Mock
    private AiWorkerRagClient ragClient;

    @Mock
    private FileService fileService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private RagDocumentService ragDocumentService;

    private RagController controller;

    @BeforeEach
    void setUp() {
        controller = new RagController(ragClient, fileService, currentUserProvider, ragDocumentService);
    }

    @Test
    void deleteDocument_shouldDeleteViaAiWorkerThenDb() {
        when(currentUserProvider.getUserId()).thenReturn(Optional.of(7L));
        when(ragDocumentService.getForUser(7L, 11L)).thenReturn(
                new RagDocumentView(11L, 7L, "t", "upload", null, null, null));
        doNothing().when(ragClient).deleteDocument(7L, 11L);
        doNothing().when(ragDocumentService).deleteForUser(7L, 11L);

        Result<Void> result = controller.deleteDocument(11L);

        assertTrue(result.isSuccess());
        verify(ragClient).deleteDocument(7L, 11L);
        verify(ragDocumentService).deleteForUser(7L, 11L);
    }

    @Test
    void deleteDocument_shouldFallbackToDbDeleteWhenAiWorkerFails() {
        when(currentUserProvider.getUserId()).thenReturn(Optional.of(7L));
        when(ragDocumentService.getForUser(7L, 12L)).thenReturn(
                new RagDocumentView(12L, 7L, "t", "upload", null, null, null));
        doThrow(new BusinessException(ErrorCode.INTERNAL_ERROR, "worker down"))
                .when(ragClient).deleteDocument(7L, 12L);
        doNothing().when(ragDocumentService).deleteForUser(7L, 12L);

        Result<Void> result = controller.deleteDocument(12L);

        assertTrue(result.isSuccess());
        verify(ragClient).deleteDocument(7L, 12L);
        verify(ragDocumentService).deleteForUser(7L, 12L);
    }

    @Test
    void deleteDocument_shouldIgnoreLocalNotFoundAfterAiWorkerDelete() {
        when(currentUserProvider.getUserId()).thenReturn(Optional.of(7L));
        when(ragDocumentService.getForUser(7L, 13L)).thenReturn(
                new RagDocumentView(13L, 7L, "t", "upload", null, null, null));
        doNothing().when(ragClient).deleteDocument(7L, 13L);
        doThrow(new BusinessException(ErrorCode.NOT_FOUND, "Document not found"))
                .when(ragDocumentService).deleteForUser(7L, 13L);

        Result<Void> result = controller.deleteDocument(13L);

        assertTrue(result.isSuccess());
        verify(ragClient).deleteDocument(7L, 13L);
        verify(ragDocumentService).deleteForUser(7L, 13L);
    }

    @Test
    void listDocuments_shouldClampPageAndSize() {
        when(currentUserProvider.getUserId()).thenReturn(Optional.of(7L));
        when(ragDocumentService.listByUserId(any(Long.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        Result<PageResponse<RagDocumentResponse>> result = controller.listDocuments(0, 999);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getData().getPage());
        assertEquals(100, result.getData().getSize());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(ragDocumentService).listByUserId(org.mockito.ArgumentMatchers.eq(7L), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(100, pageable.getPageSize());
        assertEquals("createdAt: DESC", pageable.getSort().toString());
    }

    @Test
    void listDocuments_shouldClampNegativeSizeToOne() {
        when(currentUserProvider.getUserId()).thenReturn(Optional.of(7L));
        when(ragDocumentService.listByUserId(any(Long.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        Result<PageResponse<RagDocumentResponse>> result = controller.listDocuments(-5, -8);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getData().getPage());
        assertEquals(1, result.getData().getSize());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(ragDocumentService).listByUserId(org.mockito.ArgumentMatchers.eq(7L), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(1, pageable.getPageSize());
    }

    @Test
    void query_shouldRejectNonPositiveTopK() {
        when(currentUserProvider.getUserId()).thenReturn(Optional.of(7L));
        RagQueryRequest request = RagQueryRequest.builder()
                .query("hello")
                .topK(0)
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> controller.query(request));

        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        verify(ragClient, never()).query(any(Long.class), any(String.class), any(Integer.class), any(String.class));
    }

    @Test
    void query_shouldPassValidatedTopKToClient() {
        when(currentUserProvider.getUserId()).thenReturn(Optional.of(7L));
        AiWorkerQueryResponse response = new AiWorkerQueryResponse();
        response.setChunks(List.of());
        when(ragClient.query(7L, "hello", 3, "upload")).thenReturn(response);

        Result<RagQueryResponse> result = controller.query(RagQueryRequest.builder()
                .query("hello")
                .topK(3)
                .sourceType("upload")
                .build());

        assertTrue(result.isSuccess());
        verify(ragClient).query(7L, "hello", 3, "upload");
    }
}
