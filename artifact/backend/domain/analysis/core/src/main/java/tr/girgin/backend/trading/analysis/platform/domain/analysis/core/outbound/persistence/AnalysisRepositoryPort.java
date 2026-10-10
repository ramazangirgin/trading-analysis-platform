package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisFilter;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;

/**
 * Stores analysis runs. An {@link Analysis#version()} is the optimistic lock: {@link #update} and
 * {@link #replaceImported} fail with Spring's {@code OptimisticLockingFailureException}, leaving the row
 * unchanged, when the record's version differs from the stored row's.
 */
public interface AnalysisRepositoryPort {

    /** Inserts a new record, whose version is {@code null}; the stored one starts at 0. */
    void insert(Analysis analysis);

    /** Writes the run state of a record and returns it as stored, with its new version. */
    Analysis update(Analysis analysis);

    /**
     * Rewrites an EXTERNAL record whole; unlike a platform run, its spec follows the data dir. Returns it as
     * stored, with its new version.
     */
    Analysis replaceImported(Analysis analysis);

    Optional<Analysis> findByExternalRef(String externalRef);

    Optional<Analysis> findById(AnalysisId id);

    /** Newest first. */
    List<Analysis> findAll(AnalysisFilter filter);

    List<Analysis> findByStatusIn(Collection<AnalysisStatus> statuses);
}
