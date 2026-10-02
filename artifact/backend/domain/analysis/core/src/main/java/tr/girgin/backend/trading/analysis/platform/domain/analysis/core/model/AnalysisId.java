package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/** Identifies one analysis run. Also used as the run directory name, so the format is strict. */
public record AnalysisId(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RANDOM_BYTES = 6;

    public AnalysisId {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid analysis id: " + value);
        }
    }

    public static AnalysisId newId() {
        byte[] bytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        return new AnalysisId("r_" + HexFormat.of().formatHex(bytes));
    }

    @Override
    public String toString() {
        return value;
    }
}
