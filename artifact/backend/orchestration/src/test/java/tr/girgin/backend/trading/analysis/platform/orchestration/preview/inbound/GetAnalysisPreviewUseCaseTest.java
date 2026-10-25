package tr.girgin.backend.trading.analysis.platform.orchestration.preview.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisError;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.GetPartialAnalysisUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AssetType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Rating;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound.GetReportUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.DebateSpeaker;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.Report;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportSection;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportSource;
import tr.girgin.backend.trading.analysis.platform.orchestration.preview.model.AnalysisPreview;

class GetAnalysisPreviewUseCaseTest {

    private static final ReportKey KEY = new ReportKey("NVDA", LocalDate.of(2026, 9, 25));
    private static final PreviewSettings DEFAULTS = new PreviewSettings(Set.of(ReportSection.MARKET_REPORT), 400);

    private final Analysis analysis = Analysis.queued(
                    AnalysisId.newId(),
                    new AnalysisSpec(
                            "NVDA",
                            LocalDate.of(2026, 9, 25),
                            AssetType.STOCK,
                            List.of(Analyst.MARKET),
                            "deepseek",
                            "deepseek-v4-flash",
                            "deepseek-v4-flash",
                            1,
                            1,
                            "English",
                            false),
                    new OwnerId("secret-owner"),
                    Instant.parse("2026-09-25T10:00:00Z"))
            .withDecision(Rating.BUY, "Buy it");
    private final GetPartialAnalysisUseCase getPartial = id -> {
        if (!id.equals(analysis.id())) {
            throw new AnalysisException(AnalysisError.NOT_FOUND, "Analysis not found: " + id, Map.of());
        }
        return analysis;
    };
    private Optional<Report> report = Optional.of(new Report(
            KEY,
            Map.of(
                    ReportSection.MARKET_REPORT, "open market text",
                    ReportSection.SENTIMENT_REPORT, "hidden sentiment text",
                    ReportSection.FINAL_TRADE_DECISION, "hidden decision text"),
            Map.of(DebateSpeaker.BULL, "hidden bull text", DebateSpeaker.RISK_JUDGE, "hidden judge text"),
            null,
            Set.of(ReportSource.REPORT_TREE),
            Instant.parse("2026-09-25T11:00:00Z")));

    private AnalysisPreview preview(PreviewSettings settings) {
        GetReportUseCase getReport = key -> key.equals(KEY) ? report : Optional.empty();
        return new GetAnalysisPreviewUseCase(getPartial, new PreviewCutter(getReport, settings)).get(analysis.id());
    }

    @Test
    void theDefaultPreviewHoldsTheMarketReportAndListsEverythingElseAsLocked() {
        AnalysisPreview preview = preview(DEFAULTS);

        assertThat(preview.ticker()).isEqualTo("NVDA");
        assertThat(preview.analysisDate()).isEqualTo(LocalDate.of(2026, 9, 25));
        assertThat(preview.rating()).isEqualTo(Rating.BUY);
        assertThat(preview.sections()).containsOnlyKeys(ReportSection.MARKET_REPORT);
        assertThat(preview.sections()).containsEntry(ReportSection.MARKET_REPORT, "open market text");
        assertThat(preview.lockedSections())
                .containsExactlyInAnyOrder("sentiment_report", "final_trade_decision", "bull", "risk_judge");
    }

    @Test
    void aChangedSettingChangesTheSectionsAndTheLockedTitles() {
        AnalysisPreview preview = preview(new PreviewSettings(EnumSet.of(ReportSection.SENTIMENT_REPORT), 400));

        assertThat(preview.sections()).containsOnlyKeys(ReportSection.SENTIMENT_REPORT);
        assertThat(preview.lockedSections())
                .containsExactlyInAnyOrder("market_report", "final_trade_decision", "bull", "risk_judge");
    }

    @Test
    void noTextOfAHiddenPartAppearsInTheResult() {
        String everything = preview(DEFAULTS).toString();

        assertThat(everything)
                .doesNotContain("hidden")
                .doesNotContain("secret-owner")
                .doesNotContain("Buy it");
    }

    @Test
    void aSettingForASectionTheReportLacksListsNothingForIt() {
        AnalysisPreview preview = preview(new PreviewSettings(EnumSet.of(ReportSection.NEWS_REPORT), 400));

        assertThat(preview.sections()).isEmpty();
        assertThat(preview.lockedSections()).doesNotContain("news_report");
    }

    @Test
    void aMissingReportGivesAPreviewWithoutSectionsAndTitles() {
        report = Optional.empty();

        AnalysisPreview preview = preview(DEFAULTS);

        assertThat(preview.ticker()).isEqualTo("NVDA");
        assertThat(preview.sections()).isEmpty();
        assertThat(preview.lockedSections()).isEmpty();
    }

    @Test
    void anAnalysisThatIsNotPartialIsNotFound() {
        GetAnalysisPreviewUseCase useCase =
                new GetAnalysisPreviewUseCase(getPartial, new PreviewCutter(_ -> report, DEFAULTS));

        assertThatThrownBy(() -> useCase.get(AnalysisId.newId()))
                .isInstanceOfSatisfying(
                        AnalysisException.class, e -> assertThat(e.error()).isEqualTo(AnalysisError.NOT_FOUND));
    }
}
