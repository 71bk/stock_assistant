package tw.bk.appocr.model;

import tw.bk.appcommon.enums.OcrJobStatus;

public record OcrJobView(
        Long id,
        Long statementId,
        OcrJobStatus status,
        Integer progress,
        String errorMessage) {
}
