package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisPreviewDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.RatingEnumDto;
import tr.girgin.backend.trading.analysis.platform.orchestration.preview.model.AnalysisPreview;

/** Section keys become the report's lowercase names, in the order of the report. */
@Mapper
public interface AnalysisPreviewToAnalysisPreviewDtoMapper {

    default AnalysisPreviewDto map(AnalysisPreview source) {
        Map<String, String> sections = new LinkedHashMap<>();
        new TreeMap<>(source.sections())
                .forEach((section, text) -> sections.put(section.name().toLowerCase(Locale.ROOT), text));
        return new AnalysisPreviewDto(
                source.id().value(),
                source.ticker(),
                source.analysisDate(),
                source.rating() == null
                        ? null
                        : RatingEnumDto.valueOf(source.rating().name()),
                sections,
                source.lockedSections());
    }
}
