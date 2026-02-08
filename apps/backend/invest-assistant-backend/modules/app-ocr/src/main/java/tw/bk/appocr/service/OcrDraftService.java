package tw.bk.appocr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.enums.StatementStatus;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appocr.client.AiWorkerParsedTrade;
import tw.bk.appocr.model.OcrDraftUpdate;
import tw.bk.appocr.validation.OcrDraftContext;
import tw.bk.appocr.validation.OcrDraftValidationResult;
import tw.bk.appocr.validation.OcrDraftValidatorChain;
import tw.bk.apppersistence.entity.InstrumentEntity;
import tw.bk.apppersistence.entity.StatementEntity;
import tw.bk.apppersistence.entity.StatementTradeEntity;
import tw.bk.apppersistence.repository.InstrumentRepository;
import tw.bk.apppersistence.repository.StatementRepository;
import tw.bk.apppersistence.repository.StockTradeRepository;
import tw.bk.apppersistence.repository.StatementTradeRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrDraftService {
    private static final int AMOUNT_SCALE = 6;
    private static final int PRICE_SCALE = 8;
    private static final String WARNING_SETTLEMENT_BEFORE_TRADE = "SETTLEMENT_BEFORE_TRADE";

    private final StatementTradeRepository statementTradeRepository;
    private final InstrumentRepository instrumentRepository;
    private final OcrDraftValidatorChain draftValidatorChain;
    private final ObjectMapper objectMapper;
    private final OcrRowHashService rowHashService;
    private final StatementRepository statementRepository;
    private final StockTradeRepository stockTradeRepository;

    public void saveDrafts(StatementEntity statement, List<AiWorkerParsedTrade> trades) {
        if (trades == null || trades.isEmpty()) {
            log.info("No trades to save for statement {}", statement != null ? statement.getId() : "null");
            return;
        }
        log.info("Saving {} drafts for statement {}", trades.size(), statement != null ? statement.getId() : "null");

        Set<String> seenHashes = new HashSet<>();
        if (statement != null && statement.getId() != null) {
            List<String> existingHashes = statementTradeRepository.findRowHashesByStatementId(statement.getId());
            if (existingHashes != null && !existingHashes.isEmpty()) {
                seenHashes.addAll(existingHashes);
            }
        }
        List<StatementTradeEntity> entities = new ArrayList<>();
        for (AiWorkerParsedTrade trade : trades) {
            StatementTradeEntity entity = toDraftEntity(statement, trade);
            OcrDraftContext context = new OcrDraftContext(statement, trade, entity, seenHashes);
            OcrDraftValidationResult result = OcrDraftValidationResult.withWarnings(trade.warnings());
            draftValidatorChain.validateAll(context, result);
            if (result.hasBlockingErrors()) {
                log.warn("Draft blocked by validation: {}", result.getErrors());
                continue;
            }
            entity.setWarningsJson(toJson(result.getWarnings()));
            entity.setErrorsJson(toJson(result.getErrors()));
            String rowHash = entity.getRowHash();
            if (rowHash != null && !rowHash.isBlank()) {
                seenHashes.add(rowHash);
            }
            entities.add(entity);
        }
        if (!entities.isEmpty()) {
            statementTradeRepository.saveAll(entities);
            log.info("Saved {} drafts", entities.size());
        } else {
            log.warn("No drafts saved after validation (input: {})", trades.size());
        }
    }

    private StatementTradeEntity toDraftEntity(StatementEntity statement, AiWorkerParsedTrade trade) {
        StatementTradeEntity entity = new StatementTradeEntity();
        entity.setStatementId(statement.getId());
        entity.setRawTicker(trim(trade.ticker()));
        entity.setName(trim(trade.stockName()));
        entity.setTradeDate(trade.tradeDate());
        entity.setSettlementDate(trade.settlementDate());
        entity.setSide(normalizeSide(trade.side()));
        entity.setQuantity(normalizeQuantity(trade.quantity()));
        entity.setPrice(normalizePrice(trade.price()));

        InstrumentEntity instrument = resolveInstrument(trade.ticker());
        if (instrument != null) {
            entity.setInstrumentId(instrument.getId());
            if (entity.getName() == null) {
                entity.setName(firstNonBlank(instrument.getNameZh(), instrument.getNameEn()));
            }
        }

        String currency = normalizeCurrency(trade.currency(), instrument);
        entity.setCurrency(currency);
        entity.setFee(normalizeAmount(trade.fee()));
        entity.setTax(normalizeAmount(trade.tax()));
        entity.setNetAmount(calculateNetAmount(entity.getSide(), entity.getPrice(), entity.getQuantity(),
                entity.getFee(), entity.getTax()));

        entity.setRowHash(rowHashService.buildRowHash(statement.getId(), entity));
        return entity;
    }

    private InstrumentEntity resolveInstrument(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return null;
        }
        return instrumentRepository.findFirstByTickerIgnoreCase(ticker.trim()).orElse(null);
    }

    private String normalizeSide(String side) {
        if (side == null) {
            return null;
        }
        return side.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal normalizeQuantity(BigDecimal quantity) {
        if (quantity == null) {
            return null;
        }
        return quantity.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizePrice(BigDecimal price) {
        if (price == null) {
            return null;
        }
        return price.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        }
        return amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private String normalizeCurrency(String currency, InstrumentEntity instrument) {
        if (currency != null && !currency.isBlank()) {
            return currency.trim().toUpperCase(Locale.ROOT);
        }
        if (instrument != null && instrument.getCurrency() != null) {
            return instrument.getCurrency();
        }
        return null;
    }

    private BigDecimal calculateNetAmount(String side, BigDecimal price, BigDecimal quantity,
            BigDecimal fee, BigDecimal tax) {
        if (price == null || quantity == null || side == null) {
            return null;
        }
        BigDecimal gross = price.multiply(quantity).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        BigDecimal fees = normalizeAmount(fee).add(normalizeAmount(tax))
                .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        if ("BUY".equalsIgnoreCase(side)) {
            return gross.add(fees).negate().setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        }
        return gross.subtract(fees).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    @Transactional
    public StatementTradeEntity updateDraft(Long userId, Long draftId, OcrDraftUpdate update) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
        }
        StatementTradeEntity draft = statementTradeRepository.findById(draftId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Draft not found"));
        StatementEntity statement = requireStatement(draft.getStatementId(), userId);

        if (update.instrumentId() != null) {
            draft.setInstrumentId(update.instrumentId());
        }
        if (update.rawTicker() != null) {
            draft.setRawTicker(trim(update.rawTicker()));
        }
        if (update.name() != null) {
            draft.setName(trim(update.name()));
        }
        if (update.tradeDate() != null) {
            draft.setTradeDate(update.tradeDate());
        }
        if (update.settlementDate() != null) {
            draft.setSettlementDate(update.settlementDate());
        }
        if (update.side() != null) {
            draft.setSide(update.side().name());
        }
        if (update.quantity() != null) {
            draft.setQuantity(normalizeQuantity(update.quantity()));
        }
        if (update.price() != null) {
            draft.setPrice(normalizePrice(update.price()));
        }
        if (update.currency() != null) {
            String currency = trim(update.currency());
            draft.setCurrency(currency == null ? null : currency.toUpperCase(Locale.ROOT));
        }
        if (update.fee() != null) {
            draft.setFee(normalizeAmount(update.fee()));
        }
        if (update.tax() != null) {
            draft.setTax(normalizeAmount(update.tax()));
        }

        // Re-validate using the chain
        OcrDraftContext context = new OcrDraftContext(statement, null, draft, new HashSet<>());
        OcrDraftValidationResult result = OcrDraftValidationResult.empty();
        draftValidatorChain.validateAll(context, result);

        draft.setWarningsJson(toJson(result.getWarnings()));
        draft.setErrorsJson(toJson(result.getErrors()));

        draft.setNetAmount(calculateNetAmount(
                draft.getSide(),
                draft.getPrice(),
                draft.getQuantity(),
                draft.getFee(),
                draft.getTax()));
        draft.setRowHash(rowHashService.buildRowHash(statement.getId(), draft));
        statement.setStatus(StatementStatus.DRAFT.name());
        return statementTradeRepository.save(draft);
    }

    public boolean isDuplicateDraft(StatementTradeEntity draft, Long portfolioId) {
        if (draft == null || portfolioId == null) {
            return false;
        }
        if (draft.getInstrumentId() == null
                || draft.getTradeDate() == null
                || draft.getSide() == null
                || draft.getQuantity() == null
                || draft.getPrice() == null) {
            return false;
        }
        return stockTradeRepository.existsByPortfolioIdAndInstrumentIdAndTradeDateAndSideAndQuantityAndPrice(
                portfolioId,
                draft.getInstrumentId(),
                draft.getTradeDate(),
                draft.getSide(),
                draft.getQuantity(),
                draft.getPrice());
    }

    private StatementEntity requireStatement(Long statementId, Long userId) {
        return statementRepository.findByIdAndUserId(statementId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Statement not found"));
    }

    public String toJson(List<String> warnings) {
        try {
            if (warnings == null) {
                return "[]";
            }
            return objectMapper.writeValueAsString(warnings);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
