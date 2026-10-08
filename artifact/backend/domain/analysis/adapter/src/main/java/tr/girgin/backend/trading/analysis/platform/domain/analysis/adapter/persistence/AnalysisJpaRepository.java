package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.entity.AnalysisEntity;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.entity.AnalysisIdEmbeddable;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSource;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;

interface AnalysisJpaRepository
        extends JpaRepository<AnalysisEntity, AnalysisIdEmbeddable>, JpaSpecificationExecutor<AnalysisEntity> {

    Optional<AnalysisEntity> findByExternalRef(String externalRef);

    Optional<AnalysisEntity> findByIdAndSource(AnalysisIdEmbeddable id, AnalysisSource source);

    List<AnalysisEntity> findByStatusIn(Collection<AnalysisStatus> statuses);
}
