package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisReportDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.RatingEnumDto;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.DebateSpeaker;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.Rating;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.Report;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportSection;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportSource;

class ReportToAnalysisReportDtoMapperTest {

    private static final Instant MODIFIED_AT = Instant.parse("2026-03-02T10:15:30Z");

    private final ReportToAnalysisReportDtoMapper reportToAnalysisReportDtoMapper =
            new ReportToAnalysisReportDtoMapperImpl(
                    new ReportSectionToStringMapperImpl(), new DebateSpeakerToStringMapperImpl());

    @Test
    void mapsKeysToLowerCaseInEnumOrder() {
        Map<ReportSection, String> sections = new EnumMap<>(ReportSection.class);
        sections.put(ReportSection.FINAL_TRADE_DECISION, "decision");
        sections.put(ReportSection.MARKET_REPORT, "market");
        Map<DebateSpeaker, String> debates = new EnumMap<>(DebateSpeaker.class);
        debates.put(DebateSpeaker.RISK_JUDGE, "risk");
        debates.put(DebateSpeaker.BULL, "bull");

        AnalysisReportDto dto = reportToAnalysisReportDtoMapper.map(
                report(sections, debates, null, EnumSet.noneOf(ReportSource.class)));

        assertThat(dto.sections())
                .containsExactly(Map.entry("market_report", "market"), Map.entry("final_trade_decision", "decision"));
        assertThat(dto.debates()).containsExactly(Map.entry("bull", "bull"), Map.entry("risk_judge", "risk"));
    }

    @Test
    void sortsSourcesByName() {
        AnalysisReportDto dto = reportToAnalysisReportDtoMapper.map(report(
                Map.of(),
                Map.of(),
                null,
                EnumSet.of(ReportSource.REPORT_TREE, ReportSource.DEEP_REPORT, ReportSource.GUI_RUN)));

        assertThat(dto.sources()).containsExactly("DEEP_REPORT", "GUI_RUN", "REPORT_TREE");
    }

    @Test
    void mapsRatingWithAndWithoutValue() {
        AnalysisReportDto rated = reportToAnalysisReportDtoMapper.map(
                report(Map.of(), Map.of(), Rating.OVERWEIGHT, EnumSet.noneOf(ReportSource.class)));
        AnalysisReportDto unrated = reportToAnalysisReportDtoMapper.map(
                report(Map.of(), Map.of(), null, EnumSet.noneOf(ReportSource.class)));

        assertThat(rated.rating()).isEqualTo(RatingEnumDto.OVERWEIGHT);
        assertThat(unrated.rating()).isNull();
    }

    @Test
    void mapsModifiedAt() {
        AnalysisReportDto dto = reportToAnalysisReportDtoMapper.map(
                report(Map.of(), Map.of(), null, EnumSet.noneOf(ReportSource.class)));

        assertThat(dto.modifiedAt()).isEqualTo(MODIFIED_AT);
    }

    private static Report report(
            Map<ReportSection, String> sections,
            Map<DebateSpeaker, String> debates,
            Rating rating,
            java.util.Set<ReportSource> sources) {
        return new Report(
                new ReportKey("NVDA", LocalDate.of(2026, 3, 2)), sections, debates, rating, sources, MODIFIED_AT);
    }
}
