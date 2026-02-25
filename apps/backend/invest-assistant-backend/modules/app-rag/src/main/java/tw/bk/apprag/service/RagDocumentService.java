package tw.bk.apprag.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.apppersistence.entity.RagDocumentEntity;
import tw.bk.apppersistence.repository.RagDocumentRepository;
import tw.bk.apprag.model.RagDocumentView;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RagDocumentService {
    private final RagDocumentRepository ragDocumentRepository;

    public Page<RagDocumentView> listByUserId(Long userId, Pageable pageable) {
        return ragDocumentRepository.findByUserId(userId, pageable).map(this::toView);
    }

    public RagDocumentView getForUser(Long userId, Long documentId) {
        return toView(requireOwnedEntity(userId, documentId));
    }

    @Transactional
    public void deleteForUser(Long userId, Long documentId) {
        RagDocumentEntity entity = requireOwnedEntity(userId, documentId);
        ragDocumentRepository.delete(entity);
    }

    private RagDocumentEntity requireOwnedEntity(Long userId, Long documentId) {
        RagDocumentEntity entity = ragDocumentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Document not found"));

        if (!entity.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "Access denied");
        }
        return entity;
    }

    private RagDocumentView toView(RagDocumentEntity entity) {
        return new RagDocumentView(
                entity.getId(),
                entity.getUserId(),
                entity.getTitle(),
                entity.getSourceType(),
                entity.getSourceId(),
                entity.getMeta(),
                entity.getCreatedAt());
    }
}
