package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

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
