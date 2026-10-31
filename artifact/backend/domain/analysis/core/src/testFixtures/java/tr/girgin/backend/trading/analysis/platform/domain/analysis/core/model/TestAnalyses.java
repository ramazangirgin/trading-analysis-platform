package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;

/** Builds analyses in a given state for tests, with the canonical constructor. */
public final class TestAnalyses {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final RunStats EMPTY_STATS = new RunStats(0, 0, 0, 0, null, Duration.ZERO);

    private TestAnalyses() {}

    public static AnalysisId newId() {
        byte[] bytes = new byte[6];
        RANDOM.nextBytes(bytes);
        return new AnalysisId("r_" + HexFormat.of().formatHex(bytes));
    }

    public static Analysis queued(AnalysisId id, AnalysisSpec spec, OwnerId owner, Instant now) {
        return new Analysis(
                id,
                spec,
                AnalysisStatus.QUEUED,
                AnalysisSource.PLATFORM,
                null,
                null,
                EMPTY_STATS,
                now,
                null,
                null,
                null,
                null,
                null,
                null,
                owner,
                Visibility.PRIVATE,
                null,
                null);
    }

    public static Analysis running(Analysis analysis, Instant now, String runnerRef) {
        return new Analysis(
                analysis.id(),
                analysis.spec(),
                AnalysisStatus.RUNNING,
                analysis.source(),
                analysis.rating(),
                analysis.decision(),
                analysis.stats(),
                analysis.createdAt(),
                now,
                analysis.endedAt(),
                analysis.errorCode(),
                analysis.errorMessage(),
                analysis.externalRef(),
                runnerRef,
                analysis.owner(),
                analysis.visibility(),
                analysis.visibilityChangedAt(),
                analysis.version());
    }

    public static Analysis finished(
            Analysis analysis, AnalysisStatus status, Instant now, String errorCode, String errorMessage) {
        return new Analysis(
                analysis.id(),
                analysis.spec(),
                status,
                analysis.source(),
                analysis.rating(),
                analysis.decision(),
                analysis.stats(),
                analysis.createdAt(),
                analysis.startedAt(),
                now,
                errorCode,
                errorMessage,
                analysis.externalRef(),
                analysis.runnerRef(),
                analysis.owner(),
                analysis.visibility(),
                analysis.visibilityChangedAt(),
                analysis.version());
    }

    /** An EXTERNAL record made from report files; {@code run}, the history entry that wrote them, may be null. */
    public static Analysis imported(
            AnalysisId id,
            Long version,
            OwnerId owner,
            String ticker,
            LocalDate tradeDate,
            List<Analyst> analysts,
            Rating rating,
            String decision,
            ExternalRun run,
            Instant ended) {
        AnalysisSpec spec = run == null
                ? new AnalysisSpec(
                        ticker,
                        tradeDate,
                        AssetType.STOCK,
                        analysts,
                        "unknown",
                        "unknown",
                        "unknown",
                        1,
                        1,
                        "English",
                        false)
                : new AnalysisSpec(
                        ticker,
                        tradeDate,
                        AssetType.STOCK,
                        analysts,
                        run.llmProvider(),
                        run.deepThinkLlm(),
                        run.quickThinkLlm(),
                        run.debateRounds(),
                        run.debateRounds(),
                        run.outputLanguage(),
                        false);
        Instant endedAt = ended.truncatedTo(ChronoUnit.MILLIS);
        Instant startedAt = run == null || run.startedAt() == null
                ? endedAt
                : run.startedAt().truncatedTo(ChronoUnit.MILLIS);
        return new Analysis(
                id,
                spec,
                decision != null ? AnalysisStatus.COMPLETED : AnalysisStatus.FAILED,
                AnalysisSource.EXTERNAL,
                rating,
                decision,
                run == null ? EMPTY_STATS : run.stats(),
                startedAt,
                startedAt,
                endedAt,
                decision != null ? null : "incomplete_report",
                null,
                "report:" + ticker + "/" + tradeDate,
                null,
                owner,
                Visibility.PRIVATE,
                null,
                version);
    }

    public static Analysis withStats(Analysis analysis, RunStats stats) {
        return new Analysis(
                analysis.id(),
                analysis.spec(),
                analysis.status(),
                analysis.source(),
                analysis.rating(),
                analysis.decision(),
                stats,
                analysis.createdAt(),
                analysis.startedAt(),
                analysis.endedAt(),
                analysis.errorCode(),
                analysis.errorMessage(),
                analysis.externalRef(),
                analysis.runnerRef(),
                analysis.owner(),
                analysis.visibility(),
                analysis.visibilityChangedAt(),
                analysis.version());
    }

    public static Analysis withDecision(Analysis analysis, Rating rating, String decision) {
        return new Analysis(
                analysis.id(),
                analysis.spec(),
                analysis.status(),
                analysis.source(),
                rating,
                decision,
                analysis.stats(),
                analysis.createdAt(),
                analysis.startedAt(),
                analysis.endedAt(),
                analysis.errorCode(),
                analysis.errorMessage(),
                analysis.externalRef(),
                analysis.runnerRef(),
                analysis.owner(),
                analysis.visibility(),
                analysis.visibilityChangedAt(),
                analysis.version());
    }

    public static Analysis withVisibility(Analysis analysis, Visibility visibility, Instant now) {
        return new Analysis(
                analysis.id(),
                analysis.spec(),
                analysis.status(),
                analysis.source(),
                analysis.rating(),
                analysis.decision(),
                analysis.stats(),
                analysis.createdAt(),
                analysis.startedAt(),
                analysis.endedAt(),
                analysis.errorCode(),
                analysis.errorMessage(),
                analysis.externalRef(),
                analysis.runnerRef(),
                analysis.owner(),
                visibility,
                now,
                analysis.version());
    }
}
