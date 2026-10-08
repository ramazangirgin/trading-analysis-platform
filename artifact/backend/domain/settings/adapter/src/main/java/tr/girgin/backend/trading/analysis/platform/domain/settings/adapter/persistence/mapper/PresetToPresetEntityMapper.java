package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.entity.PresetEntity;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;

@Mapper(uses = PresetIdToPresetIdEmbeddableMapper.class)
public interface PresetToPresetEntityMapper {

    PresetEntity map(Preset source);
}
