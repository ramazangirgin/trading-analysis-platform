package tr.girgin.backend.trading.analysis.platform.domain.report.adapter.datadir;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.DebateSpeaker;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportContent;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportSection;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportSource;

/** Against a fixture shaped like a real ~/.tradingagents directory. */
class FileSystemDataDirAdapterTest {

    @TempDir
    Path dataDir;

    private Path logs;

    private FileSystemDataDirAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        logs = dataDir.resolve("logs");
        write("GOOG/2026-09-28/reports/1_analysts/market.md", "# Market");
        write("GOOG/2026-09-28/reports/1_analysts/news.md", "# News");
        write("GOOG/2026-09-28/reports/2_research/bull.md", "Bull case");
        write("GOOG/2026-09-28/reports/2_research/manager.md", "Plan: hold");
        write("GOOG/2026-09-28/reports/5_portfolio/decision.md", "**Rating**: Hold");
        write("GOOG/2026-09-28/reports/complete_report.md", "# All");
        write("GOOG/2026-09-28/reports/run.json", "{}");
        write("BE/TradingAgentsStrategy_logs/full_states_log_2026-09-28.json", """
                {"company_of_interest":"BE","trade_date":"2026-09-28","market_report":"BE market",
                 "sentiment_report":"","news_report":"BE news","fundamentals_report":"BE fundamentals",
                 "investment_debate_state":{"bull_history":"bull","bear_history":"bear","history":"x",
                   "current_response":"x","judge_decision":"judge"},
                 "trader_investment_decision":"trade","risk_debate_state":{"aggressive_history":"agg",
                   "conservative_history":"con","neutral_history":"neu","history":"x","judge_decision":"final"},
                 "investment_plan":"plan","final_trade_decision":"**Rating**: Buy","future_key":1}""");
        write("MU/TradingAgentsStrategy_logs/full_states_log_2026-09-27.json", "{ truncated");
        write("MU/2026-09-27/reports/1_analysts/market.md", "# MU market");
        write("not a ticker/2026-09-27/reports/1_analysts/market.md", "ignored");
        write("NVDA/notes.txt", "ignored");
        write("../reports/MU_deep_2026-09-27.md", "# MU Derin Analiz");
        write("../reports/notes_deep_2026-09-27.md", "ignored: not a ticker");
        write("../reports/DELL_deep_latest.md", "ignored: no date");
        adapter = new FileSystemDataDirAdapter(logs, dataDir.resolve("reports"));
    }

    @Test
    void readsTheReportTree() {
        ReportContent goog = single(adapter.read(key("GOOG", "2026-09-28")));

        assertThat(goog.source()).isEqualTo(ReportSource.REPORT_TREE);
        assertThat(goog.sections()).containsEntry(ReportSection.MARKET_REPORT, "# Market")
                .containsEntry(ReportSection.NEWS_REPORT, "# News")
                .containsEntry(ReportSection.INVESTMENT_PLAN, "Plan: hold")
                .containsEntry(ReportSection.FINAL_TRADE_DECISION, "**Rating**: Hold")
                .doesNotContainKey(ReportSection.SENTIMENT_REPORT);
        assertThat(goog.debates()).containsEntry(DebateSpeaker.BULL, "Bull case")
                .containsEntry(DebateSpeaker.RESEARCH_JUDGE, "Plan: hold")
                .containsEntry(DebateSpeaker.RISK_JUDGE, "**Rating**: Hold");
    }

    @Test
    void readsTheFullStateLog() {
        ReportContent be = single(adapter.read(key("BE", "2026-09-28")));

        assertThat(be.source()).isEqualTo(ReportSource.FULL_STATE);
        assertThat(be.sections()).containsEntry(ReportSection.TRADER_INVESTMENT_PLAN, "trade")
                .containsEntry(ReportSection.FINAL_TRADE_DECISION, "**Rating**: Buy")
                .doesNotContainKey(ReportSection.SENTIMENT_REPORT);
        assertThat(be.debates()).containsEntry(DebateSpeaker.NEUTRAL, "neu")
                .containsEntry(DebateSpeaker.RISK_JUDGE, "final");
    }

    @Test
    void readsADeepAnalysisBesideTheRun() {
        assertThat(adapter.read(key("MU", "2026-09-27")))
                .filteredOn(c -> c.source() == ReportSource.DEEP_REPORT).singleElement()
                .satisfies(c -> assertThat(c.sections()).containsExactly(
                        Map.entry(ReportSection.DEEP_ANALYSIS, "# MU Derin Analiz")));
    }

    @Test
    void aCorruptFileDropsOnlyThatFile() {
        assertThat(adapter.read(key("MU", "2026-09-27"))).extracting(ReportContent::source)
                .containsExactlyInAnyOrder(ReportSource.REPORT_TREE, ReportSource.DEEP_REPORT);
    }

    @Test
    void scansValidTickersOnly() {
        assertThat(adapter.readAll()).extracting(c -> c.key().ticker() + "/" + c.source())
                .containsExactlyInAnyOrder("BE/FULL_STATE", "GOOG/REPORT_TREE", "MU/REPORT_TREE", "MU/DEEP_REPORT");
    }

    @Test
    void aMissingResultsDirIsEmpty() {
        assertThat(new FileSystemDataDirAdapter(logs.resolve("absent"), logs.resolve("absent")).readAll()).isEmpty();
    }

    private void write(String relative, String content) throws Exception {
        Path file = logs.resolve(relative).normalize();
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static ReportKey key(String ticker, String date) {
        return new ReportKey(ticker, LocalDate.parse(date));
    }

    private static ReportContent single(List<ReportContent> contents) {
        assertThat(contents).hasSize(1);
        return contents.getFirst();
    }
}
