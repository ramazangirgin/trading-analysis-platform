package tr.girgin.checkstyle.support;

/**
 * A type named in a check's parameter, simple ({@code Instant}) or fully qualified ({@code java.time.Instant}),
 * matched against a name in the source without type resolution.
 *
 * <ul>
 *   <li>A simple name matches the same name in the source, qualified or not.
 *   <li>A qualified name matches the same qualified name in the source, and a simple name that the file's
 *       imports (or {@code java.lang}, its package) tie to that type.
 * </ul>
 */
public final class TypeName {

    private final String name;
    private final boolean qualified;

    private TypeName(String name) {
        this.name = name;
        this.qualified = name.contains(".");
    }

    /** Parses a simple or qualified Java name; throws {@link IllegalArgumentException} for anything else. */
    public static TypeName parse(String text) {
        if (!Identifiers.isDottedName(text)) {
            throw new IllegalArgumentException("'" + text + "' is not a Java type name");
        }
        return new TypeName(text);
    }

    /** Whether {@code sourceName}, a dotted name read from the source, denotes this type. */
    public boolean matches(String sourceName, Imports imports) {
        if (!qualified) {
            return SourceNames.lastSegment(sourceName).equals(name);
        }
        if (sourceName.contains(".")) {
            return sourceName.equals(name);
        }
        return imports.resolvesTo(sourceName, name);
    }
}
