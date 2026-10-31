package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityError;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;

/**
 * Builds a {@link Username}: 3 to 64 ASCII characters of {@code A-Z a-z 0-9 . _ @ -}. It keeps the case it was
 * entered in; lookups ignore case with PostgreSQL's {@code lower()}. ASCII only, because its result for non-ASCII
 * letters depends on the database's locale, and which names are equal must not.
 */
@Service
class UsernameService {

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._@-]{3,64}");

    Username username(String value) {
        if (!VALID.matcher(value).matches()) {
            throw new IdentityException(
                    IdentityError.INVALID_USERNAME,
                    "Username must be 3 to 64 characters of letters, digits, '.', '_', '@' and '-'",
                    Map.of());
        }
        return new Username(value);
    }
}
