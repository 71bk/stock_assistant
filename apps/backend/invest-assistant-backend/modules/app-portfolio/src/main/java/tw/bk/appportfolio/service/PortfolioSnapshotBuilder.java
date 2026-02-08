package tw.bk.appportfolio.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tw.bk.appportfolio.model.PortfolioSnapshot;
import tw.bk.apppersistence.entity.InstrumentEntity;
import tw.bk.apppersistence.entity.PortfolioEntity;
import tw.bk.apppersistence.entity.UserPositionEntity;
import tw.bk.apppersistence.repository.InstrumentRepository;
import tw.bk.apppersistence.repository.PortfolioRepository;
import tw.bk.apppersistence.repository.UserPositionRepository;

@Service
@RequiredArgsConstructor
public class PortfolioSnapshotBuilder {
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private final PortfolioRepository portfolioRepository;
    private final UserPositionRepository positionRepository;
    private final InstrumentRepository instrumentRepository;

    public PortfolioSnapshot build(Long userId, Long portfolioId) {
        if (userId == null || portfolioId == null) {
            return null;
        }

        PortfolioEntity portfolio = portfolioRepository.findByIdAndUserId(portfolioId, userId).orElse(null);
        if (portfolio == null) {
            return null;
        }

        List<UserPositionEntity> positions = positionRepository.findByPortfolioId(portfolioId);
        if (positions.isEmpty()) {
            return null;
        }

        List<Long> instrumentIds = positions.stream()
                .map(UserPositionEntity::getInstrumentId)
                .distinct()
                .collect(Collectors.toList());

        List<InstrumentEntity> instruments = instrumentRepository.findAllById(instrumentIds);
        Map<Long, InstrumentEntity> instrumentMap = new HashMap<>();
        for (InstrumentEntity instrument : instruments) {
            instrumentMap.put(instrument.getId(), instrument);
        }

        LocalDate date = LocalDate.now(TAIPEI);
        String title = "Portfolio Snapshot - " + portfolio.getName() + " - " + date;

        StringBuilder content = new StringBuilder();
        content.append("Portfolio Snapshot\n");
        content.append("Date: ").append(date).append('\n');
        content.append("Portfolio: ").append(portfolio.getName())
                .append(" (id=").append(portfolio.getId()).append(")\n");
        content.append("Base currency: ").append(portfolio.getBaseCurrency()).append('\n');
        content.append("Positions:\n");

        for (UserPositionEntity position : positions) {
            InstrumentEntity instrument = instrumentMap.get(position.getInstrumentId());
            String ticker = instrument != null ? instrument.getTicker() : String.valueOf(position.getInstrumentId());
            String name = instrument != null
                    ? (isBlank(instrument.getNameZh()) ? instrument.getNameEn() : instrument.getNameZh())
                    : null;
            String symbolKey = instrument != null ? instrument.getSymbolKey() : null;

            content.append("- ").append(ticker);
            if (!isBlank(name)) {
                content.append(" ").append(name);
            }
            if (!isBlank(symbolKey)) {
                content.append(" (").append(symbolKey).append(")");
            }
            content.append(" qty=").append(toPlain(position.getTotalQuantity()));
            if (position.getAvgCostNative() != null) {
                content.append(" avg_cost=").append(toPlain(position.getAvgCostNative()));
            }
            if (!isBlank(position.getCurrency())) {
                content.append(" ").append(position.getCurrency());
            }
            content.append('\n');
        }

        List<String> tags = List.of(
                "portfolio",
                "snapshot",
                "portfolioId:" + portfolio.getId());

        return new PortfolioSnapshot(
                portfolio.getUserId(),
                portfolio.getId(),
                title,
                content.toString().trim(),
                tags);
    }

    private String toPlain(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
