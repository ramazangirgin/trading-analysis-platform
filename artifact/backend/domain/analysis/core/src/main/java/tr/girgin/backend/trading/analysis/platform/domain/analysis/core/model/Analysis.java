package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

import java.time.Instant;

/**
 * One analysis run and its outcome.
 * {@code externalRef} is where an EXTERNAL record came from in the data dir (see {@link ExternalAnalysis#ref()});
 * {@code runnerRef} is the runner's handle on a started run (a process or container), kept so a
 * restarted platform can find the run again.
 * {@code owner} is the user the run belongs to; {@code null} only on a record from before owners existed, or an
 * import made before the first admin, until an owner is assigned.
 * {@code visibility} is who besides the owner may read it; {@code visibilityChangedAt} is {@code null} until the
 * first change.
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
        OwnerId owner,
        Visibility visibility,
        Instant visibilityChangedAt,
        Long version) {}
