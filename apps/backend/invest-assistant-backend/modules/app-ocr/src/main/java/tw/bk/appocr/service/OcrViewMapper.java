package tw.bk.appocr.service;

import org.springframework.stereotype.Service;
import tw.bk.appocr.model.OcrDraftView;
import tw.bk.appocr.model.OcrJobView;
import tw.bk.apppersistence.entity.OcrJobEntity;
import tw.bk.apppersistence.entity.StatementTradeEntity;

@Service
public class OcrViewMapper {

    public OcrJobView toJobView(OcrJobEntity entity) {
        return new OcrJobView(
                entity.getId(),
                entity.getStatementId(),
                entity.getStatusEnum(),
                entity.getProgress(),
                entity.getErrorMessage());
    }

    public OcrDraftView toDraftView(StatementTradeEntity entity) {
        return new OcrDraftView(
                entity.getId(),
                entity.getInstrumentId(),
                entity.getRawTicker(),
                entity.getName(),
                entity.getTradeDate(),
                entity.getSettlementDate(),
                entity.getSideEnum(),
                entity.getQuantity(),
                entity.getPrice(),
                entity.getCurrency(),
                entity.getFee(),
                entity.getTax(),
                entity.getNetAmount(),
                entity.getWarningsJson(),
                entity.getErrorsJson(),
                entity.getRowHash());
    }
}
