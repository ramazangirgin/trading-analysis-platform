package tr.girgin.backend.trading.analysis.platform.domain.report.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.RunHistoryEntry;

class RunHistoryServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 28);

    private static RunHistoryEntry entry(String id, String ticker, String endedAt) {
        return new RunHistoryEntry(
                id,
                new ReportKey(ticker, DATE),
                List.of("market"),
                RunHistoryEntry.Status.COMPLETED,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                0,
                0,
                null,
                Duration.ZERO,
                Instant.parse(endedAt),
                Instant.parse(endedAt));
    }

    @Test
    void dropsEntriesWithAnInvalidTickerAndListsTheNewestFirst() {
        List<RunHistoryEntry> entries = List.of(
                entry("old", "MU", "2026-09-28T10:00:00Z"),
                entry("bad", "not a ticker", "2026-09-28T13:00:00Z"),
                entry("traversal", "../x", "2026-09-28T14:00:00Z"),
                entry("new", "GOOG", "2026-09-28T12:00:00Z"));

        assertThat(new RunHistoryService(() -> entries, new ReportKeyService()).scan())
                .extracting(RunHistoryEntry::id)
                .containsExactly("new", "old");
    }
}
