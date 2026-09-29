package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** What to analyze and with which models; becomes the runner's spec.json. */
public record AnalysisSpec(
        String ticker,
        LocalDate tradeDate,
        AssetType assetType,
        List<Analyst> analysts,
        String llmProvider,
        String deepThinkLlm,
        String quickThinkLlm,
        int maxDebateRounds,
        int maxRiskDiscussRounds,
        String outputLanguage,
        boolean checkpointEnabled) {

    public AnalysisSpec {
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(tradeDate, "tradeDate");
        Objects.requireNonNull(assetType, "assetType");
        Objects.requireNonNull(llmProvider, "llmProvider");
        Objects.requireNonNull(deepThinkLlm, "deepThinkLlm");
        Objects.requireNonNull(quickThinkLlm, "quickThinkLlm");
        Objects.requireNonNull(outputLanguage, "outputLanguage");
        ticker = ticker.strip().toUpperCase(Locale.ROOT);
        llmProvider = llmProvider.strip().toLowerCase(Locale.ROOT);
        // Upstream runs analysts in a fixed order, whatever order they were picked in.
        analysts = analysts.stream().distinct().sorted().toList();
    }
}
