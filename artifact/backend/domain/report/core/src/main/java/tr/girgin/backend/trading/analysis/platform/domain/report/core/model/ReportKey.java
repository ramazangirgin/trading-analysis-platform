package tr.girgin.backend.trading.analysis.platform.domain.report.core.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A report is identified by ticker and trade date, as upstream lays out its results directory.
 * The ticker is a directory name in the data dir, so it is held to a strict format.
 */
public record ReportKey(String ticker, LocalDate tradeDate) {

    private static final Pattern TICKER = Pattern.compile("\\^?[A-Z0-9][A-Z0-9.\\-=]{0,19}");

    public ReportKey {
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(tradeDate, "tradeDate");
        if (!TICKER.matcher(ticker).matches()) {
            throw new IllegalArgumentException("Invalid ticker: " + ticker);
        }
    }

    public static boolean isValidTicker(String ticker) {
        return ticker != null && TICKER.matcher(ticker).matches();
    }
}
