package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import java.security.SecureRandom;

/**
 * Generates random passwords: 24 characters from {@code A-Z a-z 0-9} (about 142 bits), long enough for a
 * password policy and free of characters that need quoting in a shell.
 */
class OneTimePasswordGenerator {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int LENGTH = 24;

    private final SecureRandom random = new SecureRandom();

    String generate() {
        StringBuilder password = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return password.toString();
    }
}
