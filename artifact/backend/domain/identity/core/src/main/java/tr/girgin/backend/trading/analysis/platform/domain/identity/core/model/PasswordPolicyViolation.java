package tr.girgin.backend.trading.analysis.platform.domain.identity.core.model;

/** A rule of the password policy that a password breaks. */
public enum PasswordPolicyViolation {
    TOO_SHORT,
    TOO_LONG,
    COMMON,
    SAME_AS_USERNAME
}
