package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.entity.PresetEntity;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;
import tr.girgin.backend.trading.analysis.platform.library.mapper.SharedMapperConfig;

@Mapper(config = SharedMapperConfig.class, uses = PresetIdEmbeddableToPresetIdMapper.class)
public interface PresetEntityToPresetMapper {

    Preset map(PresetEntity source);
}
