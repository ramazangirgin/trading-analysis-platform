package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import java.util.List;
import java.util.Set;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisReportDto;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.Report;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportSource;
import tr.girgin.backend.trading.analysis.platform.library.mapper.SharedMapperConfig;

@Mapper(
        config = SharedMapperConfig.class,
        uses = {ReportSectionToStringMapper.class, DebateSpeakerToStringMapper.class})
public interface ReportToAnalysisReportDtoMapper {

    @BeanMapping(ignoreUnmappedSourceProperties = "key")
    AnalysisReportDto map(Report source);

    default List<String> map(Set<ReportSource> source) {
        return source.stream().map(ReportSource::name).sorted().toList();
    }
}
