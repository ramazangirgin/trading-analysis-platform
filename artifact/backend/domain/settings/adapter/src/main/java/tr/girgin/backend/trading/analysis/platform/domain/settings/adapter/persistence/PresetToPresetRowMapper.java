package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;

@Mapper
interface PresetToPresetRowMapper {

    @Mapping(target = "id", source = "id.value")
    PresetRow map(Preset source);
}
