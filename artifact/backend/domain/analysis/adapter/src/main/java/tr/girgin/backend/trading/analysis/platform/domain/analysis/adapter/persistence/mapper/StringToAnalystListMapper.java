package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.mapper;

import java.util.Arrays;
import java.util.List;
import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;

@Mapper
public interface StringToAnalystListMapper {

    default List<Analyst> map(String source) {
        if (source == null || source.isBlank()) {
            return List.of();
        }
        return Arrays.stream(source.split(",")).map(String::trim).map(Analyst::valueOf).toList();
    }
}
