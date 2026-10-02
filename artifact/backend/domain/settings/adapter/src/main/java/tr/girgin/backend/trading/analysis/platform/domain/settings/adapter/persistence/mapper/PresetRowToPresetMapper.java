package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.row.PresetRow;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;

@Mapper(uses = StringToPresetIdMapper.class)
public interface PresetRowToPresetMapper {

    Preset map(PresetRow source);
}
