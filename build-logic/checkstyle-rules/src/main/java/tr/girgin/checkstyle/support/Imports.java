package tr.girgin.checkstyle.support;

import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The package and the type imports of one file. Checkstyle sees source text only, so this is how a simple
 * name in the code is tied to a fully qualified name in a check's parameter.
 *
 * @param packageName the file's package, empty for the default package
 * @param single single-type imports by simple name: {@code Instant} to {@code java.time.Instant}
 * @param onDemand packages of the on-demand imports: {@code java.time} for {@code import java.time.*;}
 */
public record Imports(String packageName, Map<String, String> single, Set<String> onDemand) {

    private static final String JAVA_LANG = "java.lang";

    /** Reads the declarations that precede the types of a file; {@code root} is its {@code COMPILATION_UNIT}. */
    public static Imports of(DetailAST root) {
        String packageName = "";
        Map<String, String> single = new HashMap<>();
        Set<String> onDemand = new HashSet<>();
        for (DetailAST node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getType() == TokenTypes.PACKAGE_DEF) {
                packageName = SourceNames.of(
                        node.findFirstToken(TokenTypes.ANNOTATIONS).getNextSibling());
            } else if (node.getType() == TokenTypes.IMPORT) {
                DetailAST imported = node.getFirstChild();
                if (imported.getLastChild().getType() == TokenTypes.STAR) {
                    onDemand.add(SourceNames.of(imported.getFirstChild()));
                } else {
                    String name = SourceNames.of(imported);
                    single.put(SourceNames.lastSegment(name), name);
                }
            }
        }
        return new Imports(packageName, single, onDemand);
    }

    /**
     * Whether the simple name, as written in this file, can denote the given fully qualified type: it is
     * imported by name, or the type is in {@code java.lang}, in the file's package or in an on-demand import.
     */
    public boolean resolvesTo(String simpleName, String qualifiedName) {
        String imported = single.get(simpleName);
        if (imported != null) {
            return imported.equals(qualifiedName);
        }
        String owner = qualifiedName.substring(0, qualifiedName.lastIndexOf('.'));
        return qualifiedName.equals(owner + "." + simpleName)
                && (owner.equals(JAVA_LANG) || owner.equals(packageName) || onDemand.contains(owner));
    }
}
