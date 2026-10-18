package tr.girgin.checkstyle.support;

/**
 * A pattern for the simple name of a class: a Java identifier with an optional {@code *} at its start and/or
 * its end.
 *
 * <ul>
 *   <li>{@code Exact}: the name {@code Exact}.
 *   <li>{@code Prefix*}: a name that starts with {@code Prefix}.
 *   <li>{@code *Suffix}: a name that ends with {@code Suffix}.
 *   <li>{@code *Middle*}: a name that contains {@code Middle}.
 *   <li>{@code *}: any name.
 * </ul>
 */
public final class SimpleNamePattern {

    private static final String ANY = "*";

    private final String text;
    private final boolean anyStart;
    private final boolean anyEnd;

    private SimpleNamePattern(String text, boolean anyStart, boolean anyEnd) {
        this.text = text;
        this.anyStart = anyStart;
        this.anyEnd = anyEnd;
    }

    /** Parses one pattern; throws {@link IllegalArgumentException} naming the pattern. */
    public static SimpleNamePattern parse(String source) {
        if (source.equals(ANY)) {
            return new SimpleNamePattern("", true, true);
        }
        boolean anyStart = source.startsWith(ANY);
        boolean anyEnd = source.endsWith(ANY);
        String text = source.substring(anyStart ? 1 : 0, source.length() - (anyEnd ? 1 : 0));
        if (!Identifiers.isIdentifier(text)) {
            throw new IllegalArgumentException("Invalid class name pattern '" + source
                    + "': expected a name with an optional * at its start" + " and/or its end");
        }
        return new SimpleNamePattern(text, anyStart, anyEnd);
    }

    /** Whether the simple class name is selected. */
    public boolean matches(String simpleName) {
        if (anyStart && anyEnd) {
            return simpleName.contains(text);
        }
        if (anyStart) {
            return simpleName.endsWith(text);
        }
        if (anyEnd) {
            return simpleName.startsWith(text);
        }
        return simpleName.equals(text);
    }
}
