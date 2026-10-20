package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence;

import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
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

    /**
     * A merge of the one row: a version that differs from the stored one is rejected by Hibernate. A {@code null}
     * version would be an insert that fails on the primary key with a generic error, so it is checked up front, and
     * the primary-key violation of a concurrent first insert is translated the same way.
     */
    @Override
    @Transactional
    public AnalysisDefaults save(AnalysisDefaults defaults) {
        if (defaults.version() == null && repository.existsById(AnalysisDefaultsEntity.ROW_ID)) {
            throw new OptimisticLockingFailureException("The analysis defaults were saved meanwhile");
        }
        try {
            return toDefaults.map(repository.saveAndFlush(toEntity.map(defaults)));
        } catch (DataIntegrityViolationException e) {
            throw new OptimisticLockingFailureException("The analysis defaults were saved meanwhile", e);
        }
    }
}
