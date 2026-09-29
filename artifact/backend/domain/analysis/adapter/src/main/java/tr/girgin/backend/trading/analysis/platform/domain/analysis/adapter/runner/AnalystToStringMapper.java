package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.runner;

import java.util.Locale;
import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;

@Mapper
interface AnalystToStringMapper {

    default String map(Analyst source) {
        return source.name().toLowerCase(Locale.ROOT);
    }
}
