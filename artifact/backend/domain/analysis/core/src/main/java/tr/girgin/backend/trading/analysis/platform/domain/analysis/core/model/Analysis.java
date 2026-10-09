package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * One analysis run and its outcome. Immutable: transitions return a new instance.
 * {@code externalRef} is where an EXTERNAL record came from in the data dir (see {@link ExternalAnalysis#ref()});
 * {@code runnerRef} is the runner's handle on a started run (a process or container), kept so a
 * restarted platform can find the run again.
 * {@code version} is the optimistic lock. Owned by persistence: {@code null} on a record the core builds before
 * its first save, set on every record the repository returns, and carried along unchanged by every transition.
 * A write of a record whose version differs from the stored one fails.
 */
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
        String errorMessage,
        String externalRef,
        String runnerRef,
        Long version) {

    /** Upstream's model choices are not in its report files, so a run without run history says "unknown". */
    public static final String UNKNOWN = "unknown";

    /** Error code of an imported run whose files hold no final decision. */
    public static final String INCOMPLETE_REPORT = "incomplete_report";

    public Analysis {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static Analysis queued(AnalysisId id, AnalysisSpec spec, Instant now) {
        return new Analysis(
                id,
                spec,
                AnalysisStatus.QUEUED,
                AnalysisSource.PLATFORM,
                null,
                null,
                RunStats.EMPTY,
                now,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /**
     * An EXTERNAL record for what the data dir holds. Times are cut to milliseconds, as stored, so
     * that importing the same files again yields an equal record. {@code version} is {@code null} for a new
     * import, and the stored record's version for a refresh.
     */
    public static Analysis imported(AnalysisId id, Long version, ExternalAnalysis external) {
        ExternalRun run = external.run();
        AnalysisSpec spec = run == null
                ? new AnalysisSpec(
                        external.ticker(),
                        external.tradeDate(),
                        AssetType.STOCK,
                        external.analysts(),
                        UNKNOWN,
                        UNKNOWN,
                        UNKNOWN,
                        1,
                        1,
                        "English",
                        false)
                : new AnalysisSpec(
                        external.ticker(),
                        external.tradeDate(),
                        AssetType.STOCK,
                        external.analysts(),
                        run.llmProvider(),
                        run.deepThinkLlm(),
                        run.quickThinkLlm(),
                        run.debateRounds(),
                        run.debateRounds(),
                        run.outputLanguage(),
                        false);
        AnalysisStatus status;
        String errorCode = null;
        String errorMessage = null;
        if (external.origin() == ExternalAnalysis.Origin.RUN_HISTORY) {
            status = run.status();
            errorMessage = run.errorMessage();
        } else if (external.decision() != null) {
            status = AnalysisStatus.COMPLETED;
        } else {
            status = AnalysisStatus.FAILED;
            errorCode = INCOMPLETE_REPORT;
        }
        Instant endedAt = millis(external.finishedAt());
        Instant startedAt = run == null || run.startedAt() == null ? endedAt : millis(run.startedAt());
        return new Analysis(
                id,
                spec,
                status,
                AnalysisSource.EXTERNAL,
                external.rating(),
                external.decision(),
                run == null ? RunStats.EMPTY : run.stats(),
                startedAt,
                startedAt,
                endedAt,
                errorCode,
                errorMessage,
                external.ref(),
                null,
                version);
    }

    private static Instant millis(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MILLIS);
    }

    public Analysis running(Instant now, String newRunnerRef) {
        return new Analysis(
                id,
                spec,
                AnalysisStatus.RUNNING,
                source,
                rating,
                decision,
                stats,
                createdAt,
                now,
                endedAt,
                errorCode,
                errorMessage,
                externalRef,
                newRunnerRef,
                version);
    }

    public Analysis withStats(RunStats newStats) {
        return new Analysis(
                id,
                spec,
                status,
                source,
                rating,
                decision,
                newStats,
                createdAt,
                startedAt,
                endedAt,
                errorCode,
                errorMessage,
                externalRef,
                runnerRef,
                version);
    }

    public Analysis withDecision(Rating newRating, String newDecision) {
        return new Analysis(
                id,
                spec,
                status,
                source,
                newRating,
                newDecision,
                stats,
                createdAt,
                startedAt,
                endedAt,
                errorCode,
                errorMessage,
                externalRef,
                runnerRef,
                version);
    }

    public Analysis finished(AnalysisStatus endStatus, Instant now, String code, String message) {
        if (!endStatus.isTerminal()) {
            throw new IllegalArgumentException("Not a terminal status: " + endStatus);
        }
        return new Analysis(
                id,
                spec,
                endStatus,
                source,
                rating,
                decision,
                stats,
                createdAt,
                startedAt,
                now,
                code,
                message,
                externalRef,
                runnerRef,
                version);
    }
}
