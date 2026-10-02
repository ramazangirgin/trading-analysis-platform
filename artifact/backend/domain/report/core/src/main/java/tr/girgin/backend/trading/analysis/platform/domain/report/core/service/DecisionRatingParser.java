package tr.girgin.backend.trading.analysis.platform.domain.report.core.service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.Rating;

/**
 * Reads the rating out of a Portfolio Manager decision, like upstream's {@code parse_rating}: an
 * explicit "Rating: X" line first, then a "FINAL TRANSACTION PROPOSAL: X", else REVIEW.
 */
final class DecisionRatingParser {

    private static final String RATINGS = "(buy|overweight|hold|underweight|sell)";
    private static final Pattern EXPLICIT =
            Pattern.compile("(?i)rating\\W{0,6}\\s*[:\\-]?\\W{0,6}\\s*" + RATINGS + "\\b");
    private static final Pattern PROPOSAL =
            Pattern.compile("(?i)final\\s+transaction\\s+proposal\\W{0,6}\\s*[:\\-]?\\W{0,6}\\s*" + RATINGS + "\\b");

    private DecisionRatingParser() {}

    static Rating parse(String decision) {
        for (Pattern pattern : new Pattern[] {EXPLICIT, PROPOSAL}) {
            Matcher matcher = pattern.matcher(decision);
            if (matcher.find()) {
                return Rating.valueOf(matcher.group(1).toUpperCase(Locale.ROOT));
            }
        }
        return Rating.REVIEW;
    }
}
