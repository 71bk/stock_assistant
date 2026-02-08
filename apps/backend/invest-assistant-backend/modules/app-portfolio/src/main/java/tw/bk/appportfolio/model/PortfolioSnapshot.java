package tw.bk.appportfolio.model;

import java.util.List;

public record PortfolioSnapshot(
        Long userId,
        Long portfolioId,
        String title,
        String content,
        List<String> tags) {
}
