package tr.girgin.backend.trading.analysis.platform.domain.report.adapter.datadir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.DebateSpeaker;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportContent;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportSection;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportSource;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.outbound.datadir.DataDirPort;

/**
 * Reads upstream's results directory (PLAN.md section 3.6), read-only:
 * <pre>
 * &lt;results&gt;/&lt;TICKER&gt;/&lt;DATE&gt;/reports/{1_analysts..5_portfolio}/*.md   report tree
 * &lt;results&gt;/&lt;TICKER&gt;/TradingAgentsStrategy_logs/full_states_log_&lt;DATE&gt;.json
 * </pre>
 * A missing or corrupt file only drops that file; the rest of the run is still read.
 */
@Component
class FileSystemDataDirAdapter implements DataDirPort {

    private static final Logger log = LoggerFactory.getLogger(FileSystemDataDirAdapter.class);
    private static final Pattern FULL_STATE_FILE = Pattern.compile("full_states_log_(\\d{4}-\\d{2}-\\d{2})\\.json");
    private static final String STATE_DIR = "TradingAgentsStrategy_logs";
    private static final long MAX_FILE_BYTES = 20L * 1024 * 1024;

    // Report tree file -> where its content goes. A file may feed a section and a debate turn.
    private static final Map<String, ReportSection> TREE_SECTIONS = Map.of(
            "1_analysts/market.md", ReportSection.MARKET_REPORT,
            "1_analysts/sentiment.md", ReportSection.SENTIMENT_REPORT,
            "1_analysts/news.md", ReportSection.NEWS_REPORT,
            "1_analysts/fundamentals.md", ReportSection.FUNDAMENTALS_REPORT,
            "2_research/investment_plan.md", ReportSection.INVESTMENT_PLAN,
            "3_trading/trader.md", ReportSection.TRADER_INVESTMENT_PLAN,
            "5_portfolio/decision.md", ReportSection.FINAL_TRADE_DECISION);
    private static final Map<String, DebateSpeaker> TREE_DEBATES = Map.of(
            "2_research/bull.md", DebateSpeaker.BULL,
            "2_research/bear.md", DebateSpeaker.BEAR,
            "2_research/manager.md", DebateSpeaker.RESEARCH_JUDGE,
            "4_risk/aggressive.md", DebateSpeaker.AGGRESSIVE,
            "4_risk/conservative.md", DebateSpeaker.CONSERVATIVE,
            "4_risk/neutral.md", DebateSpeaker.NEUTRAL,
            "5_portfolio/decision.md", DebateSpeaker.RISK_JUDGE);

    private final Path resultsDir;
    private final JsonMapper json = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    FileSystemDataDirAdapter(@Value("${platform.results-dir}") Path resultsDir) {
        this.resultsDir = resultsDir;
    }

    @Override
    public List<ReportContent> readAll() {
        List<ReportContent> contents = new ArrayList<>();
        for (Path tickerDir : list(resultsDir)) {
            String ticker = tickerDir.getFileName().toString();
            if (!Files.isDirectory(tickerDir) || !ReportKey.isValidTicker(ticker)) {
                continue;
            }
            for (Path child : list(tickerDir)) {
                parseDate(child.getFileName().toString())
                        .map(date -> new ReportKey(ticker, date))
                        .flatMap(this::readTree)
                        .ifPresent(contents::add);
            }
            for (Path stateFile : list(tickerDir.resolve(STATE_DIR))) {
                Matcher matcher = FULL_STATE_FILE.matcher(stateFile.getFileName().toString());
                if (matcher.matches()) {
                    parseDate(matcher.group(1))
                            .flatMap(date -> readFullState(new ReportKey(ticker, date)))
                            .ifPresent(contents::add);
                }
            }
        }
        return contents;
    }

    @Override
    public List<ReportContent> read(ReportKey key) {
        List<ReportContent> contents = new ArrayList<>();
        readTree(key).ifPresent(contents::add);
        readFullState(key).ifPresent(contents::add);
        return contents;
    }

    private Optional<ReportContent> readTree(ReportKey key) {
        Path reportsDir = resultsDir.resolve(key.ticker()).resolve(key.tradeDate().toString()).resolve("reports");
        if (!Files.isDirectory(reportsDir)) {
            return Optional.empty();
        }
        Map<ReportSection, String> sections = new EnumMap<>(ReportSection.class);
        Map<DebateSpeaker, String> debates = new EnumMap<>(DebateSpeaker.class);
        Instant[] modified = {Instant.EPOCH};
        BiConsumer<String, Path> readInto = (relative, file) -> readText(file).ifPresent(text -> {
            if (TREE_SECTIONS.containsKey(relative)) {
                sections.put(TREE_SECTIONS.get(relative), text);
            }
            if (TREE_DEBATES.containsKey(relative)) {
                debates.put(TREE_DEBATES.get(relative), text);
            }
            Instant fileTime = modifiedAt(file);
            if (fileTime.isAfter(modified[0])) {
                modified[0] = fileTime;
            }
        });
        Stream.concat(TREE_SECTIONS.keySet().stream(), TREE_DEBATES.keySet().stream())
                .distinct()
                .forEach(relative -> readInto.accept(relative, reportsDir.resolve(relative)));
        // Older runs have only manager.md for the Research Manager's plan.
        if (!sections.containsKey(ReportSection.INVESTMENT_PLAN) && debates.containsKey(DebateSpeaker.RESEARCH_JUDGE)) {
            sections.put(ReportSection.INVESTMENT_PLAN, debates.get(DebateSpeaker.RESEARCH_JUDGE));
        }
        if (sections.isEmpty() && debates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ReportContent(key, ReportSource.REPORT_TREE, sections, debates, modified[0]));
    }

    private Optional<ReportContent> readFullState(ReportKey key) {
        Path file = resultsDir.resolve(key.ticker()).resolve(STATE_DIR)
                .resolve("full_states_log_" + key.tradeDate() + ".json");
        if (!Files.isRegularFile(file) || size(file) > MAX_FILE_BYTES) {
            return Optional.empty();
        }
        FullStateLog state;
        try {
            state = json.readValue(file.toFile(), FullStateLog.class);
        } catch (JacksonException e) {
            log.warn("Skipping unreadable {}: {}", file, e.getOriginalMessage());
            return Optional.empty();
        }
        Map<ReportSection, String> sections = new EnumMap<>(ReportSection.class);
        putIfPresent(sections, ReportSection.MARKET_REPORT, state.marketReport());
        putIfPresent(sections, ReportSection.SENTIMENT_REPORT, state.sentimentReport());
        putIfPresent(sections, ReportSection.NEWS_REPORT, state.newsReport());
        putIfPresent(sections, ReportSection.FUNDAMENTALS_REPORT, state.fundamentalsReport());
        putIfPresent(sections, ReportSection.INVESTMENT_PLAN, state.investmentPlan());
        putIfPresent(sections, ReportSection.TRADER_INVESTMENT_PLAN, state.traderInvestmentDecision());
        putIfPresent(sections, ReportSection.FINAL_TRADE_DECISION, state.finalTradeDecision());
        Map<DebateSpeaker, String> debates = new EnumMap<>(DebateSpeaker.class);
        if (state.investmentDebateState() != null) {
            putIfPresent(debates, DebateSpeaker.BULL, state.investmentDebateState().bullHistory());
            putIfPresent(debates, DebateSpeaker.BEAR, state.investmentDebateState().bearHistory());
            putIfPresent(debates, DebateSpeaker.RESEARCH_JUDGE, state.investmentDebateState().judgeDecision());
        }
        if (state.riskDebateState() != null) {
            putIfPresent(debates, DebateSpeaker.AGGRESSIVE, state.riskDebateState().aggressiveHistory());
            putIfPresent(debates, DebateSpeaker.CONSERVATIVE, state.riskDebateState().conservativeHistory());
            putIfPresent(debates, DebateSpeaker.NEUTRAL, state.riskDebateState().neutralHistory());
            putIfPresent(debates, DebateSpeaker.RISK_JUDGE, state.riskDebateState().judgeDecision());
        }
        return Optional.of(new ReportContent(key, ReportSource.FULL_STATE, sections, debates, modifiedAt(file)));
    }

    private static <K> void putIfPresent(Map<K, String> target, K key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private static Optional<String> readText(Path file) {
        if (!Files.isRegularFile(file) || size(file) > MAX_FILE_BYTES) {
            return Optional.empty();
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            return text.isBlank() ? Optional.empty() : Optional.of(text);
        } catch (IOException e) {
            log.warn("Skipping unreadable {}: {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    private static Optional<LocalDate> parseDate(String value) {
        try {
            return Optional.of(LocalDate.parse(value));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static List<Path> list(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(dir)) {
            return children.sorted().toList();
        } catch (IOException e) {
            log.warn("Cannot list {}: {}", dir, e.getMessage());
            return List.of();
        }
    }

    private static Instant modifiedAt(Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return Long.MAX_VALUE;
        }
    }
}
