package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.entity.PresetEntity;
import tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.mapper.PresetEntityToPresetMapper;
import tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.mapper.PresetIdToPresetIdEmbeddableMapper;
import tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.mapper.PresetToPresetEntityMapper;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.PresetId;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.outbound.persistence.PresetRepositoryPort;

@Component
class JpaPresetRepositoryAdapter implements PresetRepositoryPort {

    private final PresetJpaRepository presetJpaRepository;
    private final PresetToPresetEntityMapper presetToPresetEntityMapper;
    private final PresetEntityToPresetMapper presetEntityToPresetMapper;
    private final PresetIdToPresetIdEmbeddableMapper presetIdToPresetIdEmbeddableMapper;

    JpaPresetRepositoryAdapter(
            PresetJpaRepository presetJpaRepository,
            PresetToPresetEntityMapper presetToPresetEntityMapper,
            PresetEntityToPresetMapper presetEntityToPresetMapper,
            PresetIdToPresetIdEmbeddableMapper presetIdToPresetIdEmbeddableMapper) {
        this.presetJpaRepository = presetJpaRepository;
        this.presetToPresetEntityMapper = presetToPresetEntityMapper;
        this.presetEntityToPresetMapper = presetEntityToPresetMapper;
        this.presetIdToPresetIdEmbeddableMapper = presetIdToPresetIdEmbeddableMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Preset> findAll(OwnerId owner) {
        Specification<PresetEntity> matching = owner == null ? Specification.unrestricted() : hasOwner(owner);
        return presetJpaRepository.findAll(matching).stream()
                .map(presetEntityToPresetMapper::map)
                .toList();
    }

    private Specification<PresetEntity> hasOwner(OwnerId owner) {
        return (root, _, builder) -> builder.equal(root.get("owner"), owner);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Preset> findById(PresetId id) {
        return presetJpaRepository
                .findById(presetIdToPresetIdEmbeddableMapper.map(id))
                .map(presetEntityToPresetMapper::map);
    }

    /**
     * An upsert: an entity with an assigned ID is merged, so it is inserted or updated. The incoming entity has
     * {@code updatedAt == null}, so the merge always makes the row dirty and every save sets {@code UPDATED_AT},
     * also when name and payload are unchanged.
     *
     * <p>The version is checked by the same call: a {@code null} version is persisted (an existing ID fails), any
     * other is merged, and Hibernate rejects a version that differs from the stored one.
     */
    @Override
    @Transactional
    public Preset save(Preset preset) {
        return presetEntityToPresetMapper.map(presetJpaRepository.saveAndFlush(presetToPresetEntityMapper.map(preset)));
    }

    @Override
    @Transactional
    public boolean delete(PresetId id) {
        return presetJpaRepository.deleteByIdCounting(presetIdToPresetIdEmbeddableMapper.map(id)) == 1;
    }

    @Override
    @Transactional
    public int assignOwnerWhereMissing(OwnerId owner) {
        List<PresetEntity> unowned = presetJpaRepository.findByOwnerIsNull();
        unowned.forEach(entity -> {
            entity.setOwner(owner);
            presetJpaRepository.saveAndFlush(entity);
        });
        return unowned.size();
    }
}
