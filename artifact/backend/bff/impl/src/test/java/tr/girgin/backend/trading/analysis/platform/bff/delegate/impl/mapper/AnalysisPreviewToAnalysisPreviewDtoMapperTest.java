package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisPreviewDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.RatingEnumDto;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Rating;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportSection;
import tr.girgin.backend.trading.analysis.platform.orchestration.preview.model.AnalysisPreview;

class AnalysisPreviewToAnalysisPreviewDtoMapperTest {

    private final AnalysisPreviewToAnalysisPreviewDtoMapper mapper =
            new AnalysisPreviewToAnalysisPreviewDtoMapperImpl(new ReportSectionToStringMapperImpl());

    @Test
    void mapsEveryField() {
        Map<ReportSection, String> sections = new EnumMap<>(ReportSection.class);
        sections.put(ReportSection.FINAL_TRADE_DECISION, "decision");
        sections.put(ReportSection.MARKET_REPORT, "market");
        AnalysisPreview preview = new AnalysisPreview(
                new AnalysisId("a1"),
                "NVDA",
                LocalDate.of(2026, 3, 2),
                Rating.OVERWEIGHT,
                sections,
                List.of("news_report", "fundamentals_report"));

        AnalysisPreviewDto dto = mapper.map(preview);

        assertThat(dto.id()).isEqualTo("a1");
        assertThat(dto.ticker()).isEqualTo("NVDA");
        assertThat(dto.analysisDate()).isEqualTo(LocalDate.of(2026, 3, 2));
        assertThat(dto.rating()).isEqualTo(RatingEnumDto.OVERWEIGHT);
        assertThat(dto.sections())
                .containsExactly(Map.entry("market_report", "market"), Map.entry("final_trade_decision", "decision"));
        assertThat(dto.lockedSections()).containsExactly("news_report", "fundamentals_report");
    }

    @Test
    void mapsMissingRatingToNull() {
        AnalysisPreview preview = new AnalysisPreview(
                new AnalysisId("a1"),
                "NVDA",
                LocalDate.of(2026, 3, 2),
                null,
                new EnumMap<>(ReportSection.class),
                List.of());

        assertThat(mapper.map(preview).rating()).isNull();
    }
}
