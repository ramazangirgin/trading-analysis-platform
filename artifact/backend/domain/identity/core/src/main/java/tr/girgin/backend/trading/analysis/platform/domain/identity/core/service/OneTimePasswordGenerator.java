package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import java.security.SecureRandom;

/**
 * Generates random passwords of a given length from {@code A-Z a-z 0-9} (about 6 bits a character), free of
 * characters that need quoting in a shell.
 */
class OneTimePasswordGenerator {

    /** The length of a generated password, unless the password policy asks for more. */
    static final int DEFAULT_LENGTH = 24;

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final SecureRandom random = new SecureRandom();

    String generate(int length) {
        StringBuilder password = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return password.toString();
    }
}
