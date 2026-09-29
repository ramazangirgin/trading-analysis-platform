package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisFilter;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;

public interface AnalysisRepositoryPort {

    void insert(Analysis analysis);

    void update(Analysis analysis);

    Optional<Analysis> findById(AnalysisId id);

    /** Newest first. */
    List<Analysis> findAll(AnalysisFilter filter);

    List<Analysis> findByStatusIn(Collection<AnalysisStatus> statuses);
}
