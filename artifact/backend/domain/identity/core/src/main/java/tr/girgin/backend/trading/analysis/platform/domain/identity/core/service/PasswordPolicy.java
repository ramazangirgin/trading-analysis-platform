package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityError;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.PasswordPolicyViolation;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;

/**
 * The password policy, in the spirit of NIST SP 800-63B: a minimum length (configurable, 12 by default), a
 * fixed maximum of 128 characters, a bundled list of the 10,000 most common passwords, and not the
 * username. There are no composition rules. Lengths count Unicode code points; spaces count.
 */
@Component
class PasswordPolicy {

    static final int MAX_LENGTH = 128;
    static final int LOWEST_MIN_LENGTH = 8;
    static final int HIGHEST_MIN_LENGTH = 64;

    private static final String COMMON_PASSWORDS = "common-passwords.txt";

    private final int minLength;
    private final Set<String> commonPasswords;

    @Autowired
    PasswordPolicy(@Value("${platform.password.min-length:12}") int minLength) {
        this.minLength = validMinLength(minLength);
        this.commonPasswords = parse(readBundledList());
    }

    PasswordPolicy(int minLength, String commonPasswordList) {
        this.minLength = validMinLength(minLength);
        this.commonPasswords = parse(commonPasswordList);
    }

    int minLength() {
        return minLength;
    }

    /**
     * Checks the password against every rule.
     *
     * @throws IdentityException {@code WEAK_PASSWORD} naming every broken rule; its message never contains
     *     the password
     */
    void check(CharSequence password, Username username) {
        Set<PasswordPolicyViolation> broken = EnumSet.noneOf(PasswordPolicyViolation.class);
        int length = password.toString().codePointCount(0, password.length());
        if (length < minLength) {
            broken.add(PasswordPolicyViolation.TOO_SHORT);
        }
        if (length > MAX_LENGTH) {
            broken.add(PasswordPolicyViolation.TOO_LONG);
        }
        if (commonPasswords.contains(password.toString().toLowerCase(Locale.ROOT))) {
            broken.add(PasswordPolicyViolation.COMMON);
        }
        if (username.value().equalsIgnoreCase(password.toString())) {
            broken.add(PasswordPolicyViolation.SAME_AS_USERNAME);
        }
        if (broken.isEmpty()) {
            return;
        }
        List<String> reasons =
                broken.stream().map(PasswordPolicyViolation::code).toList();
        throw new IdentityException(
                IdentityError.WEAK_PASSWORD,
                "The password breaks the password policy: " + String.join(", ", reasons),
                Map.of("reasons", reasons, "minLength", minLength, "maxLength", MAX_LENGTH));
    }

    private int validMinLength(int configured) {
        if (configured < LOWEST_MIN_LENGTH || configured > HIGHEST_MIN_LENGTH) {
            throw new IllegalArgumentException("platform.password.min-length must be between " + LOWEST_MIN_LENGTH
                    + " and " + HIGHEST_MIN_LENGTH + " but is " + configured);
        }
        return configured;
    }

    private Set<String> parse(String list) {
        Set<String> entries = list.lines()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .map(line -> line.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(HashSet::new));
        if (entries.isEmpty()) {
            throw new IllegalStateException("The common password list is empty");
        }
        return Set.copyOf(entries);
    }

    private String readBundledList() {
        try (InputStream in = PasswordPolicy.class.getResourceAsStream(COMMON_PASSWORDS)) {
            if (in == null) {
                throw new IllegalStateException("The common password list " + COMMON_PASSWORDS + " is missing");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
