package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

import java.time.Instant;
import java.util.Objects;

/** One analysis run and its outcome. Immutable: transitions return a new instance. */
public record Analysis(
        AnalysisId id,
        AnalysisSpec spec,
        AnalysisStatus status,
        AnalysisSource source,
        Rating rating,
        String decision,
        RunStats stats,
        Instant createdAt,
        Instant startedAt,
        Instant endedAt,
        String errorCode,
        String errorMessage) {

    public Analysis {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static Analysis queued(AnalysisId id, AnalysisSpec spec, Instant now) {
        return new Analysis(id, spec, AnalysisStatus.QUEUED, AnalysisSource.PLATFORM,
                null, null, RunStats.EMPTY, now, null, null, null, null);
    }

    /** Upstream's model choices are not in its output files, so an imported run says "unknown". */
    public static final String UNKNOWN = "unknown";

    public static Analysis imported(AnalysisId id, ExternalAnalysis external) {
        AnalysisSpec spec = new AnalysisSpec(external.ticker(), external.tradeDate(), AssetType.STOCK,
                external.analysts(), UNKNOWN, UNKNOWN, UNKNOWN, 1, 1, "English", false);
        boolean finished = external.decision() != null;
        return new Analysis(id, spec, finished ? AnalysisStatus.COMPLETED : AnalysisStatus.FAILED,
                AnalysisSource.EXTERNAL, external.rating(), external.decision(), RunStats.EMPTY,
                external.finishedAt(), external.finishedAt(), external.finishedAt(),
                finished ? null : INCOMPLETE_REPORT, null);
    }

    /** Error code of an imported run whose files hold no final decision. */
    public static final String INCOMPLETE_REPORT = "incomplete_report";

    public Analysis running(Instant now) {
        return new Analysis(id, spec, AnalysisStatus.RUNNING, source, rating, decision, stats,
                createdAt, now, endedAt, errorCode, errorMessage);
    }

    public Analysis withStats(RunStats newStats) {
        return new Analysis(id, spec, status, source, rating, decision, newStats,
                createdAt, startedAt, endedAt, errorCode, errorMessage);
    }

    public Analysis withDecision(Rating newRating, String newDecision) {
        return new Analysis(id, spec, status, source, newRating, newDecision, stats,
                createdAt, startedAt, endedAt, errorCode, errorMessage);
    }

    public Analysis finished(AnalysisStatus endStatus, Instant now, String code, String message) {
        if (!endStatus.isTerminal()) {
            throw new IllegalArgumentException("Not a terminal status: " + endStatus);
        }
        return new Analysis(id, spec, endStatus, source, rating, decision, stats,
                createdAt, startedAt, now, code, message);
    }
}
