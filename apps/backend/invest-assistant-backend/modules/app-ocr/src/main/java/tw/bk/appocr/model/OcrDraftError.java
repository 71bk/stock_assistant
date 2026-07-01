package tw.bk.appocr.model;

public record OcrDraftError<T>(T draftId, String reason) {
}
