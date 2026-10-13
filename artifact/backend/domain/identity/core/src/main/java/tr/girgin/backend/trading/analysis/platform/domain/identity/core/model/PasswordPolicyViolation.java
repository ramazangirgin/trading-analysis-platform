package tr.girgin.backend.trading.analysis.platform.domain.identity.core.model;

import java.util.Locale;

/** A rule of the password policy that a password breaks. */
public enum PasswordPolicyViolation {
    TOO_SHORT,
    TOO_LONG,
    COMMON,
    SAME_AS_USERNAME;

    /** The reason code that goes into the error's params, e.g. {@code too_short}. */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
