package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import java.util.Map;
import org.mapstruct.Mapper;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.PresetDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.PresetOwnerDto;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;

/** The payload is stored as JSON text; the API hands it back as an object. */
@Mapper
public interface PresetToPresetDtoMapper {

    JsonMapper JSON = JsonMapper.builder().build();

    default PresetDto map(Preset source, Map<UserId, Username> usernames) {
        Map<String, Object> values = JSON.readValue(source.payload(), new TypeReference<Map<String, Object>>() {});
        OwnerId owner = source.owner();
        PresetOwnerDto ownerDto = null;
        if (owner != null) {
            Username username = usernames.get(new UserId(owner.value()));
            ownerDto = new PresetOwnerDto(owner.value(), username == null ? null : username.value());
        }
        return new PresetDto(
                source.id().value(), source.name(), values, ownerDto, source.updatedAt(), source.version());
    }
}
