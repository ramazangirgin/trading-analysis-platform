package tr.girgin.backend.trading.analysis.platform.domain.report.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.DebateSpeaker;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.Rating;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.Report;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportContent;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportSection;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportSource;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.outbound.datadir.DataDirPort;

class ReportServiceTest {

    private static final ReportKey GOOG = new ReportKey("GOOG", LocalDate.of(2026, 9, 28));
    private static final ReportKey MU = new ReportKey("MU", LocalDate.of(2026, 9, 27));

    private final List<ReportContent> files = List.of(
            new ReportContent(
                    GOOG,
                    ReportSource.REPORT_TREE,
                    Map.of(ReportSection.MARKET_REPORT, "tree market", ReportSection.NEWS_REPORT, "tree news"),
                    Map.of(DebateSpeaker.BULL, "tree bull"),
                    Instant.parse("2026-09-28T10:00:00Z")),
            new ReportContent(
                    GOOG,
                    ReportSource.FULL_STATE,
                    Map.of(
                            ReportSection.MARKET_REPORT,
                            "state market",
                            ReportSection.NEWS_REPORT,
                            " ",
                            ReportSection.FINAL_TRADE_DECISION,
                            "**Rating**: Underweight\nTrim."),
                    Map.of(DebateSpeaker.BEAR, "state bear"),
                    Instant.parse("2026-09-28T11:00:00Z")),
            new ReportContent(
                    MU,
                    ReportSource.REPORT_TREE,
                    Map.of(ReportSection.MARKET_REPORT, "mu market"),
                    Map.of(),
                    Instant.parse("2026-09-27T09:00:00Z")));

    private final ReportService service = new ReportService(new DataDirPort() {
        @Override
        public List<ReportContent> readAll() {
            return files;
        }

        @Override
        public List<ReportContent> read(ReportKey key) {
            return files.stream().filter(f -> f.key().equals(key)).toList();
        }
    });

    @Test
    void mergesSourcesPreferringTheFullStateButNotItsBlanks() {
        Report goog = service.get(GOOG).orElseThrow();

        assertThat(goog.sections())
                .containsEntry(ReportSection.MARKET_REPORT, "state market")
                .containsEntry(ReportSection.NEWS_REPORT, "tree news");
        assertThat(goog.debates()).containsOnlyKeys(DebateSpeaker.BULL, DebateSpeaker.BEAR);
        assertThat(goog.rating()).isEqualTo(Rating.UNDERWEIGHT);
        assertThat(goog.sources()).containsExactlyInAnyOrder(ReportSource.REPORT_TREE, ReportSource.FULL_STATE);
        assertThat(goog.modifiedAt()).isEqualTo(Instant.parse("2026-09-28T11:00:00Z"));
    }

    @Test
    void aReportWithoutDecisionHasNoRating() {
        Report mu = service.get(MU).orElseThrow();

        assertThat(mu.hasDecision()).isFalse();
        assertThat(mu.rating()).isNull();
    }

    @Test
    void scansNewestTradeDateFirst() {
        assertThat(service.scan()).extracting(Report::key).containsExactly(GOOG, MU);
    }

    @Test
    void unknownKeysAreEmpty() {
        assertThat(service.get(new ReportKey("AAPL", LocalDate.of(2026, 1, 2)))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "**Rating**: Overweight|OVERWEIGHT",
                "Rating - BUY. Build gradually|BUY",
                "### Final Rating<NL>**Hold**|HOLD",
                "FINAL TRANSACTION PROPOSAL: **SELL**|SELL",
                "We like it a lot|REVIEW"
            })
    void parsesRatingsLikeUpstream(String decision, Rating expected) {
        assertThat(DecisionRatingParser.parse(decision.replace("<NL>", "\n"))).isEqualTo(expected);
    }
}
