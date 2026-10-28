package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.entity.PresetEntity;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;
import tr.girgin.backend.trading.analysis.platform.library.mapper.SharedMapperConfig;

@Mapper(config = SharedMapperConfig.class, uses = PresetIdToPresetIdEmbeddableMapper.class)
public interface PresetToPresetEntityMapper {

    /** The audited {@code updatedAt} is set by auditing, never from the domain. */
    @BeanMapping(ignoreUnmappedSourceProperties = "updatedAt")
    @Mapping(target = "updatedAt", ignore = true)
    PresetEntity map(Preset source);
}
