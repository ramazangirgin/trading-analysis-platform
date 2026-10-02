package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.mapper;

import java.util.List;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;

/** Stored as a comma-separated list of enum names, e.g. "MARKET,NEWS". */
@Mapper
public interface AnalystListToStringMapper {

    default String map(List<Analyst> source) {
        return source.stream().map(Analyst::name).collect(Collectors.joining(","));
    }
}
