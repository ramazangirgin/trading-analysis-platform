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

    private final AnalysisDefaultsJpaRepository analysisDefaultsJpaRepository;
    private final AnalysisDefaultsToAnalysisDefaultsEntityMapper analysisDefaultsToAnalysisDefaultsEntityMapper;
    private final AnalysisDefaultsEntityToAnalysisDefaultsMapper analysisDefaultsEntityToAnalysisDefaultsMapper;

    JpaAnalysisDefaultsRepositoryAdapter(
            AnalysisDefaultsJpaRepository analysisDefaultsJpaRepository,
            AnalysisDefaultsToAnalysisDefaultsEntityMapper analysisDefaultsToAnalysisDefaultsEntityMapper,
            AnalysisDefaultsEntityToAnalysisDefaultsMapper analysisDefaultsEntityToAnalysisDefaultsMapper) {
        this.analysisDefaultsJpaRepository = analysisDefaultsJpaRepository;
        this.analysisDefaultsToAnalysisDefaultsEntityMapper = analysisDefaultsToAnalysisDefaultsEntityMapper;
        this.analysisDefaultsEntityToAnalysisDefaultsMapper = analysisDefaultsEntityToAnalysisDefaultsMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AnalysisDefaults> find() {
        return analysisDefaultsJpaRepository
                .findById(AnalysisDefaultsEntity.ROW_ID)
                .map(analysisDefaultsEntityToAnalysisDefaultsMapper::map);
    }

    @Override
    @Transactional
    public AnalysisDefaults save(AnalysisDefaults defaults) {
        return analysisDefaultsEntityToAnalysisDefaultsMapper.map(analysisDefaultsJpaRepository.saveAndFlush(
                analysisDefaultsToAnalysisDefaultsEntityMapper.map(defaults)));
    }
}
