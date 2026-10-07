package tr.girgin.backend.trading.analysis.platform.domain.identity.core.model;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityError;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException;

/**
 * A login name: 3 to 64 ASCII characters of {@code A-Z a-z 0-9 . _ @ -}. It keeps the case it was
 * entered in; lookups ignore case. ASCII only, because SQLite's {@code lower()} folds ASCII only and
 * both databases must agree on which names are equal.
 */
public record Username(String value) {

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._@-]{3,64}");

    public Username {
        Objects.requireNonNull(value, "value");
        if (!VALID.matcher(value).matches()) {
            throw new IdentityException(
                    IdentityError.INVALID_USERNAME,
                    "Username must be 3 to 64 characters of letters, digits, '.', '_', '@' and '-'",
                    Map.of());
        }
    }
}
