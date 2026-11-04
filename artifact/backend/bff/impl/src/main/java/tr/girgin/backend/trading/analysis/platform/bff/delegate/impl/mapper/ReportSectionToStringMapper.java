package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import java.util.Locale;
import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportSection;
import tr.girgin.backend.trading.analysis.platform.library.mapper.SharedMapperConfig;

/** The protocol's lowercase name, the one report_section events use. */
@Mapper(config = SharedMapperConfig.class)
public interface ReportSectionToStringMapper {

    default String map(ReportSection source) {
        return source.name().toLowerCase(Locale.ROOT);
    }
}
