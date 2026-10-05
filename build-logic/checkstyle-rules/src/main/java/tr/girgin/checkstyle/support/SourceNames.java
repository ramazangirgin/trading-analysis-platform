package tr.girgin.checkstyle.support;

import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;

/** Reads dotted names (<code>java.lang.System</code>, <code>TimeUnit.SECONDS</code>) from the syntax tree. */
public final class SourceNames {

    private SourceNames() {}

    /**
     * The dotted name an expression spells out, or {@code null} when it is anything else (a call, a literal,
     * {@code this}, a parenthesized expression, ...).
     */
    public static String of(DetailAST expression) {
        if (expression.getType() == TokenTypes.IDENT) {
            return expression.getText();
        }
        if (expression.getType() != TokenTypes.DOT || expression.getLastChild().getType() != TokenTypes.IDENT) {
            return null;
        }
        String qualifier = of(expression.getFirstChild());
        return qualifier == null
                ? null
                : qualifier + "." + expression.getLastChild().getText();
    }

    /** Whether the node is part of a package or import declaration. */
    public static boolean inDeclarationHeader(DetailAST node) {
        for (DetailAST parent = node.getParent(); parent != null; parent = parent.getParent()) {
            int type = parent.getType();
            if (type == TokenTypes.IMPORT || type == TokenTypes.STATIC_IMPORT || type == TokenTypes.PACKAGE_DEF) {
                return true;
            }
        }
        return false;
    }

    /** The part of a dotted name after its last dot, or the whole name when it has none. */
    public static String lastSegment(String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }
}
