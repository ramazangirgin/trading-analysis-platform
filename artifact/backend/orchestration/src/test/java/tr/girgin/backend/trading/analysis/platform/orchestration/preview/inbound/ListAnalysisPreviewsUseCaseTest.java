package tr.girgin.backend.trading.analysis.platform.orchestration.preview.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.ListPartialAnalysesUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AssetType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.PartialAnalysisPage;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.Report;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportSection;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportSource;
import tr.girgin.backend.trading.analysis.platform.orchestration.preview.model.AnalysisPreviewPage;

class ListAnalysisPreviewsUseCaseTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 25);

    private final List<int[]> requests = new ArrayList<>();
    private final Analysis analysis = Analysis.queued(
            AnalysisId.newId(),
            new AnalysisSpec(
                    "NVDA",
                    DATE,
                    AssetType.STOCK,
                    List.of(Analyst.MARKET),
                    "deepseek",
                    "deepseek-v4-flash",
                    "deepseek-v4-flash",
                    1,
                    1,
                    "English",
                    false),
            new OwnerId("user-a"),
            Instant.parse("2026-09-25T10:00:00Z"));
    private final ListPartialAnalysesUseCase listPartial = (page, size) -> {
        requests.add(new int[] {page, size});
        return new PartialAnalysisPage(List.of(analysis), true);
    };

    private AnalysisPreviewPage list(String marketReport, int excerptLength) {
        Report report = new Report(
                new ReportKey("NVDA", DATE),
                Map.of(ReportSection.MARKET_REPORT, marketReport, ReportSection.NEWS_REPORT, "hidden news"),
                Map.of(),
                null,
                Set.of(ReportSource.REPORT_TREE),
                Instant.parse("2026-09-25T11:00:00Z"));
        PreviewSettings settings = new PreviewSettings(Set.of(ReportSection.MARKET_REPORT), excerptLength);
        return new ListAnalysisPreviewsUseCase(listPartial, new PreviewCutter(_ -> Optional.of(report), settings))
                .list(2, 12);
    }

    @Test
    void pagingPassesThrough() {
        AnalysisPreviewPage page = list("short", 400);

        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst()).containsExactly(2, 12);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(analysis.id());
            assertThat(item.ticker()).isEqualTo("NVDA");
            assertThat(item.analysisDate()).isEqualTo(DATE);
        });
    }

    @Test
    void aShortSectionIsTheWholeExcerpt() {
        assertThat(list("short", 400).items().getFirst().excerpt()).isEqualTo("short");
    }

    @Test
    void theExcerptIsCutAtTheLastLineBreakBeforeTheLength() {
        String text = "first line\nsecond line\nthird line";

        String excerpt = list(text, 25).items().getFirst().excerpt();

        assertThat(excerpt).isEqualTo("first line\nsecond line…");
        assertThat(excerpt.length() - 1).isLessThanOrEqualTo(25);
    }

    @Test
    void aTextWithoutLineBreakIsCutAtTheLength() {
        assertThat(list("abcdefghij", 4).items().getFirst().excerpt()).isEqualTo("abcd…");
    }

    @Test
    void noTextOfAHiddenSectionIsInTheExcerpt() {
        assertThat(list("open", 400).toString()).doesNotContain("hidden");
    }
}
