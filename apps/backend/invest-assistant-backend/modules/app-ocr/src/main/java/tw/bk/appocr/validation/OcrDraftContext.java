package tw.bk.appocr.validation;

import java.util.Set;
import tw.bk.appocr.client.AiWorkerParsedTrade;
import tw.bk.apppersistence.entity.StatementEntity;
import tw.bk.apppersistence.entity.StatementTradeEntity;

public record OcrDraftContext(
        StatementEntity statement,
        AiWorkerParsedTrade sourceTrade,
        StatementTradeEntity draft,
        Set<String> seenHashes) {
}
