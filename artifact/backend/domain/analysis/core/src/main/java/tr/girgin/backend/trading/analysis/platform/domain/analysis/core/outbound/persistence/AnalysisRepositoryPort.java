package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisFilter;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;

/** Stores analysis runs. */
public interface AnalysisRepositoryPort {

    void insert(Analysis analysis);

    void update(Analysis analysis);

    /** Rewrites an EXTERNAL record whole; unlike a platform run, its spec follows the data dir. */
    void replaceImported(Analysis analysis);

    Optional<Analysis> findByExternalRef(String externalRef);

    Optional<Analysis> findById(AnalysisId id);

    /** Newest first. */
    List<Analysis> findAll(AnalysisFilter filter);

    List<Analysis> findByStatusIn(Collection<AnalysisStatus> statuses);
}
