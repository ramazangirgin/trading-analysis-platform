package tr.girgin.backend.trading.analysis.platform.domain.identity.core.model;

/** An encoded password hash such as {@code {bcrypt}$2a$...}. Its text never appears in a log line. */
public record PasswordHash(String value) {

    @Override
    public String toString() {
        return "PasswordHash[***]";
    }
}
