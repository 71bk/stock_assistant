package tw.bk.apprag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.apppersistence.entity.RagDocumentEntity;
import tw.bk.apppersistence.repository.RagDocumentRepository;
import tw.bk.apprag.model.RagDocumentView;

@ExtendWith(MockitoExtension.class)
class RagDocumentServiceTest {

    @Mock
    private RagDocumentRepository ragDocumentRepository;

    private RagDocumentService service;

    @BeforeEach
    void setUp() {
        service = new RagDocumentService(ragDocumentRepository);
    }

    @Test
    void listByUserId_shouldMapRepositoryPageToView() {
        RagDocumentEntity entity = new RagDocumentEntity();
        entity.setId(10L);
        entity.setUserId(7L);
        entity.setTitle("Portfolio Note");
        entity.setSourceType("portfolio");
        entity.setSourceId("p-1");
        entity.setMeta(Map.of("lang", "zh-TW"));
        entity.setCreatedAt(Instant.parse("2026-02-25T00:00:00Z"));

        PageRequest pageable = PageRequest.of(0, 20);
        when(ragDocumentRepository.findByUserId(7L, pageable)).thenReturn(new PageImpl<>(java.util.List.of(entity)));

        Page<RagDocumentView> result = service.listByUserId(7L, pageable);

        assertEquals(1, result.getTotalElements());
        RagDocumentView view = result.getContent().get(0);
        assertEquals(10L, view.id());
        assertEquals(7L, view.userId());
        assertEquals("Portfolio Note", view.title());
        assertEquals("portfolio", view.sourceType());
        assertEquals("p-1", view.sourceId());
    }

    @Test
    void getForUser_shouldReturnViewWhenOwnerMatches() {
        RagDocumentEntity entity = new RagDocumentEntity();
        entity.setId(11L);
        entity.setUserId(8L);
        entity.setTitle("My Doc");
        entity.setSourceType("note");
        entity.setCreatedAt(Instant.now());

        when(ragDocumentRepository.findById(11L)).thenReturn(Optional.of(entity));

        RagDocumentView view = service.getForUser(8L, 11L);

        assertNotNull(view);
        assertEquals(11L, view.id());
        assertEquals(8L, view.userId());
    }

    @Test
    void getForUser_shouldThrowForbiddenWhenOwnerDiffers() {
        RagDocumentEntity entity = new RagDocumentEntity();
        entity.setId(12L);
        entity.setUserId(9L);
        when(ragDocumentRepository.findById(12L)).thenReturn(Optional.of(entity));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getForUser(8L, 12L));

        assertEquals(ErrorCode.AUTH_FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void deleteForUser_shouldDeleteWhenOwnerMatches() {
        RagDocumentEntity entity = new RagDocumentEntity();
        entity.setId(13L);
        entity.setUserId(10L);
        when(ragDocumentRepository.findById(13L)).thenReturn(Optional.of(entity));

        service.deleteForUser(10L, 13L);

        verify(ragDocumentRepository).delete(entity);
    }

    @Test
    void deleteForUser_shouldThrowNotFoundWhenMissing() {
        when(ragDocumentRepository.findById(404L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteForUser(10L, 404L));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }
}
