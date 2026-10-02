package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import java.util.Map;
import org.mapstruct.Mapper;
import tools.jackson.databind.json.JsonMapper;

@Mapper
public interface MapToJsonStringMapper {

    JsonMapper JSON = JsonMapper.builder().build();

    default String map(Map<String, Object> source) {
        return JSON.writeValueAsString(source);
    }
}
