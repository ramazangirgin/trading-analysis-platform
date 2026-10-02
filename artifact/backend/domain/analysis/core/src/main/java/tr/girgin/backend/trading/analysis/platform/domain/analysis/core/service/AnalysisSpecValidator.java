package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import java.time.LocalDate;
import java.util.Map;
import java.util.regex.Pattern;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisError;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;

/**
 * Domain checks on a spec. The values end up as process arguments and path components in the
 * runner, so they are held to strict formats (PLAN.md section 7).
 */
final class AnalysisSpecValidator {

    // Exchange suffixes (BRK.B, THYAO.IS, 7203.T), crypto pairs (BTC-USD) and indices (^GSPC).
    private static final Pattern TICKER = Pattern.compile("\\^?[A-Z0-9][A-Z0-9.\\-=]{0,19}");
    private static final Pattern PROVIDER = Pattern.compile("[a-z0-9_\\-]{1,32}");
    private static final Pattern MODEL = Pattern.compile("[A-Za-z0-9._:/@\\-]{1,128}");
    private static final Pattern LANGUAGE = Pattern.compile("[\\p{L} ()\\-]{1,40}");
    private static final LocalDate EARLIEST = LocalDate.of(1990, 1, 1);
    private static final int MAX_ROUNDS = 10;

    private AnalysisSpecValidator() {
    }

    static void validate(AnalysisSpec spec, LocalDate today) {
        require(TICKER.matcher(spec.ticker()).matches(), "ticker");
        require(!spec.tradeDate().isAfter(today) && !spec.tradeDate().isBefore(EARLIEST), "tradeDate");
        require(!spec.analysts().isEmpty(), "analysts");
        require(PROVIDER.matcher(spec.llmProvider()).matches(), "llmProvider");
        require(MODEL.matcher(spec.deepThinkLlm()).matches(), "deepThinkLlm");
        require(MODEL.matcher(spec.quickThinkLlm()).matches(), "quickThinkLlm");
        require(inRange(spec.maxDebateRounds()), "maxDebateRounds");
        require(inRange(spec.maxRiskDiscussRounds()), "maxRiskDiscussRounds");
        require(LANGUAGE.matcher(spec.outputLanguage()).matches(), "outputLanguage");
    }

    private static boolean inRange(int rounds) {
        return rounds >= 1 && rounds <= MAX_ROUNDS;
    }

    private static void require(boolean valid, String field) {
        if (!valid) {
            throw new AnalysisException(AnalysisError.INVALID_SPEC, "Invalid " + field, Map.of("field", field));
        }
    }
}
