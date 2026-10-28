package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.PresetValuesDto;
import tr.girgin.backend.trading.analysis.platform.library.mapper.SharedMapperConfig;

@Mapper(config = SharedMapperConfig.class)
public abstract class PresetValuesDtoToJsonStringMapper {

    @Autowired
    private JsonMapper jsonMapper;

    public String map(PresetValuesDto source) {
        return jsonMapper.writeValueAsString(source);
    }
}
