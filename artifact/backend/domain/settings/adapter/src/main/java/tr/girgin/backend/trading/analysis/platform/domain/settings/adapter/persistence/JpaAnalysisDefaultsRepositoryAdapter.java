package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence;

import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.entity.AnalysisDefaultsEntity;
import tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.mapper.AnalysisDefaultsEntityToAnalysisDefaultsMapper;
import tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.mapper.AnalysisDefaultsToAnalysisDefaultsEntityMapper;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.AnalysisDefaults;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.outbound.persistence.AnalysisDefaultsRepositoryPort;

@Component
class JpaAnalysisDefaultsRepositoryAdapter implements AnalysisDefaultsRepositoryPort {

    private final AnalysisDefaultsJpaRepository repository;
    private final AnalysisDefaultsToAnalysisDefaultsEntityMapper toEntity;
    private final AnalysisDefaultsEntityToAnalysisDefaultsMapper toDefaults;

    JpaAnalysisDefaultsRepositoryAdapter(
            AnalysisDefaultsJpaRepository repository,
            AnalysisDefaultsToAnalysisDefaultsEntityMapper toEntity,
            AnalysisDefaultsEntityToAnalysisDefaultsMapper toDefaults) {
        this.repository = repository;
        this.toEntity = toEntity;
        this.toDefaults = toDefaults;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AnalysisDefaults> find() {
        return repository.findById(AnalysisDefaultsEntity.ROW_ID).map(toDefaults::map);
    }

    @Override
    @Transactional
    public AnalysisDefaults save(AnalysisDefaults defaults) {
        return toDefaults.map(repository.saveAndFlush(toEntity.map(defaults)));
    }
}
