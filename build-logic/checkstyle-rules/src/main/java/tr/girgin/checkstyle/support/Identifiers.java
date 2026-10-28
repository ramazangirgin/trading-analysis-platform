package tr.girgin.checkstyle.support;

import java.util.regex.Pattern;

/** Syntax of the names a check's parameters may contain. */
public final class Identifiers {

    private static final String IDENTIFIER = "[\\p{L}_$][\\p{L}\\p{N}_$]*";
    private static final Pattern SINGLE_IDENTIFIER = Pattern.compile(IDENTIFIER);
    private static final Pattern DOTTED_NAME = Pattern.compile(IDENTIFIER + "(\\." + IDENTIFIER + ")*");
    private static final Pattern NAME_OR_PREFIX = Pattern.compile("(" + IDENTIFIER + ")?\\*|" + IDENTIFIER);

    private Identifiers() {}

    /** A single Java identifier. */
    public static boolean isIdentifier(String text) {
        return SINGLE_IDENTIFIER.matcher(text).matches();
    }

    /** A Java identifier or several, separated by dots. */
    static boolean isDottedName(String text) {
        return DOTTED_NAME.matcher(text).matches();
    }

    /** A Java identifier, or the start of one followed by {@code *}. */
    static boolean isNameOrPrefix(String text) {
        return NAME_OR_PREFIX.matcher(text).matches();
    }
}
