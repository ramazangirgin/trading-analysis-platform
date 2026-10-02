package tr.girgin.backend.trading.analysis.platform.domain.report.adapter.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.RunHistoryEntry;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.RunHistoryEntry.Status;

/** Against a runs.json shaped like the one found in a real ~/.tradingagents. */
class JsonRunHistoryAdapterTest {

    @TempDir
    private Path dataDir;

    @Test
    void readsFinishedRuns() throws Exception {
        write("""
                {"version": 1, "current_id": "a2cdeaa9e373", "order": ["6d104666417a", "a2cdeaa9e373"],
                 "runs": {
                  "6d104666417a": {"id": "6d104666417a", "ticker": "NVDIA", "date": "2026-09-27",
                    "selected": ["market", "news"], "status": "error", "started_at": 1790542499.2366211,
                    "ended_at": 1790542522.574634, "error": "OpenAIError: Missing credentials.", "decision": null,
                    "stats": {"llm_calls": 0, "tool_calls": 0, "tokens_in": 0, "tokens_out": 0, "cost_usd": 0.0,
                      "elapsed_s": 23.34},
                    "params": {"provider": "openai", "quick_model": "gpt-5.4-mini", "deep_model": "gpt-5.5",
                      "research_depth": "3", "output_language": "Turkish"}},
                  "a2cdeaa9e373": {"id": "a2cdeaa9e373", "ticker": "nvda", "date": "2026-09-27",
                    "selected": ["market"], "status": "completed", "started_at": 1790543476.7372181,
                    "ended_at": 1790544875.390643, "error": null, "reports": {"market_report": "# NVDA"},
                    "stats": {"llm_calls": 10, "tool_calls": 0, "tokens_in": 68794, "tokens_out": 28916,
                      "cost_usd": 0.0277, "elapsed_s": 1398.65},
                    "params": {"provider": "deepseek", "quick_model": "deepseek-v4-flash",
                      "deep_model": "deepseek-v4-pro", "research_depth": 5}}}}""");

        var entries = new JsonRunHistoryAdapter(dataDir).readAll();

        assertThat(entries).hasSize(2);
        RunHistoryEntry failed = entries.getFirst();
        assertThat(failed.key()).isEqualTo(new ReportKey("NVDIA", LocalDate.of(2026, 9, 27)));
        assertThat(failed.status()).isEqualTo(Status.FAILED);
        assertThat(failed.error()).isEqualTo("OpenAIError: Missing credentials.");
        assertThat(failed.analysts()).containsExactly("market", "news");
        assertThat(failed.researchDepth()).isEqualTo(3);
        assertThat(failed.outputLanguage()).isEqualTo("Turkish");
        assertThat(failed.startedAt()).isEqualTo(Instant.parse("2026-09-27T20:54:59.237Z"));

        RunHistoryEntry completed = entries.getLast();
        assertThat(completed.key().ticker()).isEqualTo("NVDA");
        assertThat(completed.status()).isEqualTo(Status.COMPLETED);
        assertThat(completed.error()).isNull();
        assertThat(completed.llmProvider()).isEqualTo("deepseek");
        assertThat(completed.deepThinkLlm()).isEqualTo("deepseek-v4-pro");
        assertThat(completed.quickThinkLlm()).isEqualTo("deepseek-v4-flash");
        assertThat(completed.researchDepth()).isEqualTo(5);
        assertThat(completed.outputLanguage()).isNull();
        assertThat(completed.tokensIn()).isEqualTo(68_794);
        assertThat(completed.costUsd()).isEqualTo(new BigDecimal("0.0277"));
        assertThat(completed.elapsed()).isEqualTo(Duration.ofMillis(1_398_650));
    }

    @Test
    void skipsRunsInProgressAndEntriesThatMakeNoSense() throws Exception {
        write("""
                {"runs": {
                  "a": {"ticker": "MU", "date": "2026-09-27", "status": "running", "started_at": 1790545213.5},
                  "b": {"ticker": "not a ticker", "date": "2026-09-27", "status": "error", "ended_at": 1790545213.5},
                  "c": {"ticker": "MU", "date": "yesterday", "status": "error", "ended_at": 1790545213.5},
                  "d": {"ticker": "MU", "date": "2026-09-27", "status": "cancelled", "ended_at": 1790545213.5}}}""");

        assertThat(new JsonRunHistoryAdapter(dataDir).readAll()).singleElement().satisfies(entry -> {
            assertThat(entry.id()).isEqualTo("d");
            assertThat(entry.status()).isEqualTo(Status.STOPPED);
            assertThat(entry.startedAt()).isNull();
            assertThat(entry.costUsd()).isNull();
            assertThat(entry.elapsed()).isEqualTo(Duration.ZERO);
        });
    }

    @Test
    void aMissingOrCorruptFileIsAnEmptyHistory() throws Exception {
        assertThat(new JsonRunHistoryAdapter(dataDir).readAll()).isEmpty();

        write("{ truncated");

        assertThat(new JsonRunHistoryAdapter(dataDir).readAll()).isEmpty();
    }

    private void write(String content) throws Exception {
        Files.writeString(dataDir.resolve("runs.json"), content);
    }
}
