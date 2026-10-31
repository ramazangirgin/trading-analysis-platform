package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSource;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AssetType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalAnalysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalRun;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Rating;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunOutcome;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunStats;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Visibility;

/** Builds an analysis and moves it from one state to the next. Every transition keeps the record's version. */
@Service
class AnalysisTransitionService {

    // Upstream's model choices are not in its report files, so a run without run history says "unknown".
    static final String UNKNOWN = "unknown";
    static final String INCOMPLETE_REPORT = "incomplete_report";

    private static final RunStats NO_STATS = new RunStats(0, 0, 0, 0, null, Duration.ZERO);

    private final AnalysisSpecService specs;

    AnalysisTransitionService(AnalysisSpecService specs) {
        this.specs = specs;
    }

    Analysis queued(AnalysisId id, AnalysisSpec spec, OwnerId owner, Instant now) {
        return new Analysis(
                id,
                spec,
                AnalysisStatus.QUEUED,
                AnalysisSource.PLATFORM,
                null,
                null,
                NO_STATS,
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

    /**
     * An EXTERNAL record for what the data dir holds. Times are cut to milliseconds, as stored, so that importing
     * the same files again yields an equal record. {@code version} is {@code null} for a new import, and the
     * stored record's version for a refresh. {@code run} may be {@code null}: the models are then "unknown".
     */
    Analysis imported(AnalysisId id, Long version, OwnerId owner, ExternalAnalysis external, String externalRef) {
        ExternalRun run = external.run();
        AnalysisSpec spec = specs.normalized(
                run == null
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
                                false));
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
                run == null ? NO_STATS : run.stats(),
                startedAt,
                startedAt,
                endedAt,
                errorCode,
                errorMessage,
                externalRef,
                null,
                owner,
                Visibility.PRIVATE,
                null,
                version);
    }

    Analysis running(Analysis analysis, Instant now, String runnerRef) {
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

    Analysis withStats(Analysis analysis, RunStats stats) {
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

    Analysis withDecision(Analysis analysis, Rating rating, String decision) {
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

    Analysis withVisibility(Analysis analysis, Visibility visibility, Instant now) {
        return withVisibilityAt(analysis, visibility, now);
    }

    /** The record with the visibility, and its time, of {@code other}: a refresh from files never changes them. */
    Analysis withVisibilityOf(Analysis analysis, Analysis other) {
        return withVisibilityAt(analysis, other.visibility(), other.visibilityChangedAt());
    }

    Analysis finished(Analysis analysis, AnalysisStatus status, Instant now, String code, String message) {
        if (!isTerminal(status)) {
            throw new IllegalArgumentException("Not a terminal status: " + status);
        }
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
                code,
                message,
                analysis.externalRef(),
                analysis.runnerRef(),
                analysis.owner(),
                analysis.visibility(),
                analysis.visibilityChangedAt(),
                analysis.version());
    }

    boolean isTerminal(AnalysisStatus status) {
        return status == AnalysisStatus.COMPLETED
                || status == AnalysisStatus.STOPPED
                || status == AnalysisStatus.FAILED;
    }

    AnalysisStatus toStatus(RunOutcome outcome) {
        return switch (outcome) {
            case COMPLETED -> AnalysisStatus.COMPLETED;
            case STOPPED -> AnalysisStatus.STOPPED;
            case FAILED -> AnalysisStatus.FAILED;
        };
    }

    private Analysis withVisibilityAt(Analysis analysis, Visibility visibility, Instant changedAt) {
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
                changedAt,
                analysis.version());
    }

    private Instant millis(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MILLIS);
    }
}
