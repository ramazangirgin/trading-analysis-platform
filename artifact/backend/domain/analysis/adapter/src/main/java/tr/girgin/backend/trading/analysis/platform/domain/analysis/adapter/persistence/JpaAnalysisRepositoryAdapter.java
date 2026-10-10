package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.entity.AnalysisEntity;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.mapper.AnalysisEntityToAnalysisMapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.mapper.AnalysisIdToAnalysisIdEmbeddableMapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.mapper.AnalysisToAnalysisEntityMapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisFilter;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSource;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.persistence.AnalysisRepositoryPort;

@Component
class JpaAnalysisRepositoryAdapter implements AnalysisRepositoryPort {

    private static final int LIST_LIMIT = 500;

    private final AnalysisJpaRepository repository;
    private final AnalysisToAnalysisEntityMapper toEntity;
    private final AnalysisEntityToAnalysisMapper toAnalysis;
    private final AnalysisIdToAnalysisIdEmbeddableMapper toEntityId;

    JpaAnalysisRepositoryAdapter(
            AnalysisJpaRepository repository,
            AnalysisToAnalysisEntityMapper toEntity,
            AnalysisEntityToAnalysisMapper toAnalysis,
            AnalysisIdToAnalysisIdEmbeddableMapper toEntityId) {
        this.repository = repository;
        this.toEntity = toEntity;
        this.toAnalysis = toAnalysis;
        this.toEntityId = toEntityId;
    }

    /** A plain persist: an ID that exists already fails, as the INSERT did. */
    @Override
    public void insert(Analysis analysis) {
        repository.save(toEntity.map(analysis));
    }

    /**
     * The spec and creation time never change after insert; dirty checking writes the run state. A managed
     * entity's version cannot be changed, so the record's version is compared with the loaded one first.
     */
    @Override
    @Transactional
    public Analysis update(Analysis analysis) {
        AnalysisEntity stored = repository
                .findById(toEntityId.map(analysis.id()))
                .orElseThrow(() -> new IllegalStateException("No analysis " + analysis.id() + " to update"));
        requireVersion(stored, analysis);
        stored.applyRunState(toEntity.map(analysis));
        return toAnalysis.map(repository.saveAndFlush(stored));
    }

    /** An imported record follows its files, spec and creation time included. */
    @Override
    @Transactional
    public Analysis replaceImported(Analysis analysis) {
        AnalysisEntity stored = repository
                .findByIdAndSource(toEntityId.map(analysis.id()), AnalysisSource.EXTERNAL)
                .orElseThrow(() -> new IllegalStateException("No imported analysis " + analysis.id() + " to replace"));
        requireVersion(stored, analysis);
        stored.replaceImported(toEntity.map(analysis));
        return toAnalysis.map(repository.saveAndFlush(stored));
    }

    /**
     * The flush's {@code UPDATE ... WHERE VERSION = ?} covers the time between the load and the flush.
     *
     * @throws OptimisticLockingFailureException when the record's version is not the stored one
     */
    private static void requireVersion(AnalysisEntity stored, Analysis analysis) {
        if (!Objects.equals(stored.getVersion(), analysis.version())) {
            throw new OptimisticLockingFailureException(
                    "Analysis " + analysis.id() + " was changed by someone else since it was read");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Analysis> findByExternalRef(String externalRef) {
        return repository.findByExternalRef(externalRef).map(toAnalysis::map);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Analysis> findById(AnalysisId id) {
        return repository.findById(toEntityId.map(id)).map(toAnalysis::map);
    }

    /** One SELECT with ORDER BY and LIMIT and no COUNT query, which a Page would add. */
    @Override
    @Transactional(readOnly = true)
    public List<Analysis> findAll(AnalysisFilter filter) {
        return repository
                .findBy(
                        matching(filter),
                        query -> query.sortBy(Sort.by(Sort.Direction.DESC, "createdAt"))
                                .limit(LIST_LIMIT)
                                .all())
                .stream()
                .map(toAnalysis::map)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Analysis> findByStatusIn(Collection<AnalysisStatus> statuses) {
        if (statuses.isEmpty()) {
            return List.of();
        }
        return repository.findByStatusIn(statuses).stream().map(toAnalysis::map).toList();
    }

    private static Specification<AnalysisEntity> matching(AnalysisFilter filter) {
        Specification<AnalysisEntity> matching = (_, _, builder) -> builder.conjunction();
        if (filter.status() != null) {
            matching = matching.and(hasStatus(filter.status()));
        }
        if (filter.ticker() != null) {
            matching = matching.and(hasTicker(filter.ticker()));
        }
        return matching;
    }

    private static Specification<AnalysisEntity> hasStatus(AnalysisStatus status) {
        return (root, _, builder) -> builder.equal(root.get("status"), status);
    }

    private static Specification<AnalysisEntity> hasTicker(String ticker) {
        return (root, _, builder) -> builder.equal(root.get("spec").get("ticker"), ticker);
    }
}
