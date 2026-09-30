package tr.girgin.backend.trading.analysis.platform.domain.report.adapter.history;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.RunHistoryEntry;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.RunHistoryEntry.Status;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.outbound.history.RunHistoryPort;

/**
 * Reads {@code <data-dir>/runs.json}, the run history a third-party UI keeps (PLAN.md section 3.6):
 * <pre>
 * {"version": 1, "runs": {"&lt;id&gt;": {"ticker", "date", "selected", "status", "started_at", "ended_at",
 *   "error", "stats": {"llm_calls", …, "cost_usd", "elapsed_s"}, "params": {"provider", "deep_model", …}}}}
 * </pre>
 * A missing or unreadable file is an empty history; an entry that does not make sense is skipped.
 */
@Component
class JsonRunHistoryAdapter implements RunHistoryPort {

    private static final Logger log = LoggerFactory.getLogger(JsonRunHistoryAdapter.class);
    private static final long MAX_FILE_BYTES = 50L * 1024 * 1024;

    private final Path file;
    private final JsonMapper json = JsonMapper.builder().build();

    JsonRunHistoryAdapter(@Value("${platform.data-dir}") Path dataDir) {
        this.file = dataDir.resolve("runs.json");
    }

    @Override
    public List<RunHistoryEntry> readAll() {
        if (!Files.isRegularFile(file) || size() > MAX_FILE_BYTES) {
            return List.of();
        }
        JsonNode root;
        try {
            root = json.readTree(file.toFile());
        } catch (JacksonException e) {
            log.warn("Skipping unreadable {}: {}", file, e.getOriginalMessage());
            return List.of();
        }
        List<RunHistoryEntry> entries = new ArrayList<>();
        for (var run : root.path("runs").properties()) {
            entry(run.getKey(), run.getValue()).ifPresent(entries::add);
        }
        return entries;
    }

    private static Optional<RunHistoryEntry> entry(String fallbackId, JsonNode run) {
        String ticker = text(run, "ticker");
        Optional<LocalDate> date = date(text(run, "date"));
        Optional<Status> status = status(text(run, "status"));
        Instant endedAt = epochSeconds(run.path("ended_at"));
        if (ticker == null || date.isEmpty() || status.isEmpty() || endedAt == null) {
            return Optional.empty();
        }
        ticker = ticker.strip().toUpperCase(Locale.ROOT);
        if (!ReportKey.isValidTicker(ticker)) {
            return Optional.empty();
        }
        String id = Optional.ofNullable(text(run, "id")).orElse(fallbackId);
        JsonNode params = run.path("params");
        JsonNode stats = run.path("stats");
        List<String> analysts = new ArrayList<>();
        run.path("selected").forEach(a -> {
            if (a.isString()) {
                analysts.add(a.asString());
            }
        });
        JsonNode elapsed = stats.path("elapsed_s");
        JsonNode cost = stats.path("cost_usd");
        return Optional.of(new RunHistoryEntry(
                id,
                new ReportKey(ticker, date.get()),
                analysts,
                status.get(),
                text(run, "error"),
                text(params, "provider"),
                text(params, "deep_model"),
                text(params, "quick_model"),
                integer(params.path("research_depth")),
                text(params, "output_language"),
                stats.path("llm_calls").asLong(0),
                stats.path("tool_calls").asLong(0),
                stats.path("tokens_in").asLong(0),
                stats.path("tokens_out").asLong(0),
                // Same conversion as a REAL read back from the database, so a rescan sees no change.
                cost.isNumber() ? BigDecimal.valueOf(cost.asDouble()) : null,
                elapsed.isNumber() ? Duration.ofMillis(Math.round(elapsed.asDouble() * 1000)) : Duration.ZERO,
                epochSeconds(run.path("started_at")),
                endedAt));
    }

    private static Optional<Status> status(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "completed", "done", "success" -> Optional.of(Status.COMPLETED);
            case "error", "failed" -> Optional.of(Status.FAILED);
            case "stopped", "cancelled", "canceled", "interrupted" -> Optional.of(Status.STOPPED);
            // queued / running: not finished yet, a later scan picks it up.
            default -> Optional.empty();
        };
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() && !value.asString().isBlank() ? value.asString() : null;
    }

    private static Integer integer(JsonNode node) {
        if (node.isNumber()) {
            return node.asInt();
        }
        if (node.isString()) {
            try {
                return Integer.valueOf(node.asString().strip());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static Instant epochSeconds(JsonNode node) {
        if (!node.isNumber()) {
            return null;
        }
        return Instant.ofEpochMilli(Math.round(node.asDouble() * 1000));
    }

    private static Optional<LocalDate> date(String value) {
        try {
            return value == null ? Optional.empty() : Optional.of(LocalDate.parse(value));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private long size() {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return Long.MAX_VALUE;
        }
    }
}
