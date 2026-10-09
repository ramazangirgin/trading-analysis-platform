package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.mapper.PresetEntityToPresetMapper;
import tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.mapper.PresetIdToPresetIdEmbeddableMapper;
import tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.mapper.PresetToPresetEntityMapper;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.PresetId;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.outbound.persistence.PresetRepositoryPort;

@Component
class JpaPresetRepositoryAdapter implements PresetRepositoryPort {

    private final PresetJpaRepository repository;
    private final PresetToPresetEntityMapper toEntity;
    private final PresetEntityToPresetMapper toPreset;
    private final PresetIdToPresetIdEmbeddableMapper toEntityId;

    JpaPresetRepositoryAdapter(
            PresetJpaRepository repository,
            PresetToPresetEntityMapper toEntity,
            PresetEntityToPresetMapper toPreset,
            PresetIdToPresetIdEmbeddableMapper toEntityId) {
        this.repository = repository;
        this.toEntity = toEntity;
        this.toPreset = toPreset;
        this.toEntityId = toEntityId;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Preset> findAll() {
        return repository.findAll().stream().map(toPreset::map).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Preset> findById(PresetId id) {
        return repository.findById(toEntityId.map(id)).map(toPreset::map);
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
        return toPreset.map(repository.saveAndFlush(toEntity.map(preset)));
    }

    @Override
    @Transactional
    public boolean delete(PresetId id) {
        return repository.deleteByIdCounting(toEntityId.map(id)) == 1;
    }
}
