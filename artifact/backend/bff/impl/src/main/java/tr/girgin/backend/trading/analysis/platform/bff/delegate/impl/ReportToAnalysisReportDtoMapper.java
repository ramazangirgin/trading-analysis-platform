package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisReportDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.RatingDto;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.Report;

/** Keys become the protocol's lowercase names, the ones report_section events use. */
@Mapper
interface ReportToAnalysisReportDtoMapper {

    default AnalysisReportDto map(Report source) {
        Map<String, String> sections = new LinkedHashMap<>();
        source.sections().forEach((section, text) -> sections.put(section.name().toLowerCase(Locale.ROOT), text));
        Map<String, String> debates = new LinkedHashMap<>();
        source.debates().forEach((speaker, text) -> debates.put(speaker.name().toLowerCase(Locale.ROOT), text));
        return new AnalysisReportDto(sections, debates,
                source.rating() == null ? null : RatingDto.valueOf(source.rating().name()),
                source.sources().stream().map(Enum::name).sorted().toList(),
                source.modifiedAt());
    }
}
