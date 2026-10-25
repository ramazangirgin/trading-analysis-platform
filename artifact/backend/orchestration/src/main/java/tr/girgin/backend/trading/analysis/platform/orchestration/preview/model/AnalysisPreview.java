package tr.girgin.backend.trading.analysis.platform.orchestration.preview.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Rating;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportSection;

/**
 * What a visitor may read of a partial analysis. {@code lockedSections} holds the report keys of the parts that
 * are cut (no text, nothing else); the owner is deliberately not part of it.
 */
public record AnalysisPreview(
        AnalysisId id,
        String ticker,
        LocalDate analysisDate,
        Rating rating,
        Map<ReportSection, String> sections,
        List<String> lockedSections) {

    public AnalysisPreview {
        sections = Map.copyOf(sections);
        lockedSections = List.copyOf(lockedSections);
    }
}
