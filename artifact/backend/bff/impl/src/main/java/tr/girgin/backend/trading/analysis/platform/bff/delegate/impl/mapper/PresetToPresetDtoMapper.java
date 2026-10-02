package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import java.util.Map;
import org.mapstruct.Mapper;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.PresetDto;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;

/** The payload is stored as JSON text; the API hands it back as an object. */
@Mapper
public interface PresetToPresetDtoMapper {

    JsonMapper JSON = JsonMapper.builder().build();

    default PresetDto map(Preset source) {
        Map<String, Object> values = JSON.readValue(source.payload(), new TypeReference<Map<String, Object>>() {});
        return new PresetDto(source.id().value(), source.name(), values, source.updatedAt());
    }
}
