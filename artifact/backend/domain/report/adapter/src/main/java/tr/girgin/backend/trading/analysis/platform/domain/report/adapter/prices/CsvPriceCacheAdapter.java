package tr.girgin.backend.trading.analysis.platform.domain.report.adapter.prices;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.PriceBar;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.outbound.prices.PriceCachePort;

/**
 * Reads upstream's yfinance cache, read-only:
 * <pre>
 * &lt;cache&gt;/&lt;TICKER&gt;-YFin-data-&lt;start&gt;-&lt;end&gt;.csv   Date,Close,High,Low,Open,Volume (daily)
 * &lt;cache&gt;/&lt;TICKER&gt;-YFin-data.csv                  older upstream versions, no range in the name
 * </pre>
 * Of a ticker's files the one reaching furthest is read. The ticker is only compared with file
 * names in the directory, never joined into a path. Rows that do not parse are skipped.
 */
@Component
class CsvPriceCacheAdapter implements PriceCachePort {

    private static final Logger log = LoggerFactory.getLogger(CsvPriceCacheAdapter.class);
    private static final Pattern RANGED =
            Pattern.compile("(?<ticker>.+)-YFin-data-(?<from>\\d{4}-\\d{2}-\\d{2})-(?<to>\\d{4}-\\d{2}-\\d{2})\\.csv");
    private static final String UNRANGED_SUFFIX = "-YFin-data.csv";
    private static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    // Dates may carry a time ("2024-01-02 00:00:00-05:00"); the day is the first ten characters.
    private static final int ISO_DATE_LENGTH = "yyyy-MM-dd".length();
    private static final List<String> COLUMNS = List.of("Date", "Open", "High", "Low", "Close", "Volume");

    private final Path cacheDir;

    CsvPriceCacheAdapter(@Value("${platform.cache-dir}") Path cacheDir) {
        this.cacheDir = cacheDir;
    }

    @Override
    public List<PriceBar> read(String ticker, LocalDate upTo) {
        return newestFile(ticker).map(CsvPriceCacheAdapter::parse).orElse(List.of());
    }

    private Optional<Path> newestFile(String ticker) {
        if (!Files.isDirectory(cacheDir)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.list(cacheDir)) {
            List<Path> candidates = files.filter(Files::isRegularFile).toList();
            Optional<Path> ranged = candidates.stream()
                    .map(file -> RANGED.matcher(file.getFileName().toString()))
                    .filter(m -> m.matches() && m.group("ticker").equals(ticker))
                    .max(Comparator.comparing((Matcher m) -> m.group("to")))
                    .map(m -> cacheDir.resolve(m.group(0)));
            return ranged.or(() -> candidates.stream()
                    .filter(file -> file.getFileName().toString().equals(ticker + UNRANGED_SUFFIX))
                    .findFirst());
        } catch (IOException | UncheckedIOException e) {
            log.warn("Cannot list the price cache {}: {}", cacheDir, e.getMessage());
            return Optional.empty();
        }
    }

    static List<PriceBar> parse(Path file) {
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                log.warn("Skipping oversized price cache file {}", file);
                return List.of();
            }
            return parse(file, Files.readAllLines(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("Cannot read price cache file {}: {}", file, e.getMessage());
            return List.of();
        }
    }

    private static List<PriceBar> parse(Path file, List<String> lines) {
        if (lines.isEmpty()) {
            return List.of();
        }
        List<String> header = List.of(lines.getFirst().trim().split(","));
        Map<String, Integer> index = IntStream.range(0, header.size())
                .boxed()
                .collect(Collectors.toMap(header::get, Function.identity(), (a, _) -> a));
        if (!index.keySet().containsAll(COLUMNS)) {
            log.warn("Price cache file {} lacks columns {}", file, COLUMNS);
            return List.of();
        }
        List<PriceBar> bars = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            row(line.split(",", -1), index).ifPresent(bars::add);
        }
        return bars;
    }

    private static Optional<PriceBar> row(String[] cells, Map<String, Integer> index) {
        try {
            double close = Double.parseDouble(cells[index.get("Close")]);
            if (!Double.isFinite(close)) {
                return Optional.empty();
            }
            return Optional.of(new PriceBar(
                    LocalDate.parse(cells[index.get("Date")].trim().substring(0, ISO_DATE_LENGTH)),
                    Double.parseDouble(cells[index.get("Open")]),
                    Double.parseDouble(cells[index.get("High")]),
                    Double.parseDouble(cells[index.get("Low")]),
                    close,
                    (long) Double.parseDouble(cells[index.get("Volume")])));
        } catch (IllegalArgumentException | DateTimeParseException | IndexOutOfBoundsException _) {
            // A malformed number or date, or a short row: not a trading day we can use.
            return Optional.empty();
        }
    }
}
