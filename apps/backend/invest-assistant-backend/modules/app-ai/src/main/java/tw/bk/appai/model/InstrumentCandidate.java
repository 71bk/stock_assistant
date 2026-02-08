package tw.bk.appai.model;

public record InstrumentCandidate(
        String symbolKey,
        String ticker,
        String name,
        String market,
        String exchange,
        String assetType) {
}
