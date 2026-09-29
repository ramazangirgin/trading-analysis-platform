package tr.girgin.backend.trading.analysis.platform.orchestration.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalAnalysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalRegistration;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalRegistration.Outcome;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.Rating;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.Report;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportSection;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportSource;

class ImportExistingRunsServiceTest {

    private final List<ExternalAnalysis> registered = new ArrayList<>();

    @Test
    void registersEveryReportWithWhatItsFilesTell() {
        Report goog = new Report(new ReportKey("GOOG", LocalDate.of(2026, 9, 28)),
                Map.of(ReportSection.MARKET_REPORT, "m", ReportSection.SENTIMENT_REPORT, "s",
                        ReportSection.FINAL_TRADE_DECISION, "**Rating**: Hold"),
                Map.of(), Rating.HOLD, Set.of(ReportSource.REPORT_TREE), Instant.parse("2026-09-28T12:00:00Z"));
        Report mu = new Report(new ReportKey("MU", LocalDate.of(2026, 9, 27)),
                Map.of(ReportSection.NEWS_REPORT, "n"), Map.of(), null, Set.of(ReportSource.REPORT_TREE),
                Instant.parse("2026-09-27T12:00:00Z"));
        ImportExistingRunsService service = new ImportExistingRunsService(() -> List.of(goog, mu), external -> {
            registered.add(external);
            Outcome outcome = external.ticker().equals("GOOG") ? Outcome.CREATED : Outcome.UNCHANGED;
            return new ExternalRegistration(Analysis.imported(AnalysisId.newId(), external), outcome);
        }, false);

        ImportResult result = service.importExistingRuns();

        assertThat(result).isEqualTo(new ImportResult(2, 1, 0, 1));
        assertThat(registered.getFirst().analysts()).containsExactlyInAnyOrder(Analyst.MARKET, Analyst.SOCIAL);
        assertThat(registered.getFirst().rating())
                .isEqualTo(tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Rating.HOLD);
        assertThat(registered.getFirst().decision()).isEqualTo("**Rating**: Hold");
        assertThat(registered.get(1).decision()).isNull();
        assertThat(registered.get(1).rating()).isNull();
    }
}
