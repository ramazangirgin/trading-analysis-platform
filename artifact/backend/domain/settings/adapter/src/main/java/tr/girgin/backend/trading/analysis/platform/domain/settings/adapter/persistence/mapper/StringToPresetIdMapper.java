package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.PresetId;

@Mapper
public interface StringToPresetIdMapper {

    default PresetId map(String source) {
        return new PresetId(source);
    }
}
