package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;

@Mapper(uses = StringToPresetIdMapper.class)
interface PresetRowToPresetMapper {

    Preset map(PresetRow source);
}
