package tr.girgin.checkstyle;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import tr.girgin.checkstyle.support.SimpleNamePattern;

/**
 * Reports a variable whose declared type matches one of the configured name patterns but whose name is not
 * the type's name in lower camel case. The types are configured with {@code types}; see
 * build-logic/checkstyle-rules/docs/VariableNamedAfterTypeCheck.md.
 */
public class VariableNamedAfterTypeCheck extends AbstractCheck {

    public static final String MSG_NAME = "variable.named.after.type";

    private static final String UNNAMED = "_";
    private static final Pattern LOWER_CASE_START = Pattern.compile("\\p{Ll}.*");

    private List<SimpleNamePattern> types = List.of();
    private boolean allowQualifier;
    private String suggestion = "";

    /** The simple name patterns ({@code *Service}) of the types whose variables must be named after them. */
    public void setTypes(String... patterns) {
        types = Arrays.stream(patterns).map(SimpleNamePattern::parse).toList();
    }

    /** Whether a lower-case qualifier before the type's name is accepted ({@code strictUserService}). */
    public void setAllowQualifier(boolean allowQualifier) {
        this.allowQualifier = allowQualifier;
    }

    /** Appended to every message: how to fix the finding. */
    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    @Override
    protected void finishLocalSetup() {
        if (types.isEmpty()) {
            throw new IllegalArgumentException("VariableNamedAfterTypeCheck: 'types' is required");
        }
    }

    @Override
    public int[] getDefaultTokens() {
        return getRequiredTokens();
    }

    @Override
    public int[] getAcceptableTokens() {
        return getRequiredTokens();
    }

    @Override
    public int[] getRequiredTokens() {
        return new int[] {
            TokenTypes.VARIABLE_DEF, TokenTypes.PARAMETER_DEF, TokenTypes.RECORD_COMPONENT_DEF, TokenTypes.RESOURCE
        };
    }

    @Override
    public void visitToken(DetailAST variable) {
        DetailAST name = variable.findFirstToken(TokenTypes.IDENT);
        String typeName = declaredTypeName(variable);
        if (typeName == null
                || name.getText().equals(UNNAMED)
                || isConstant(variable)
                || types.stream().noneMatch(pattern -> pattern.matches(typeName))) {
            return;
        }
        String expected = lowerCamelCase(typeName);
        if (name.getText().equals(expected) || allowQualifier && hasQualifier(name.getText(), typeName)) {
            return;
        }
        log(
                name,
                MSG_NAME,
                idPrefix(),
                name.getText(),
                typeName,
                expected,
                allowQualifier ? " (or '<qualifier>" + typeName + "')" : "",
                suggestion.isEmpty() ? "" : " " + suggestion);
    }

    /** The last segment of the type's name; {@code null} for {@code var}, arrays, primitives and implicit lambda parameters. */
    private static String declaredTypeName(DetailAST variable) {
        DetailAST type = variable.findFirstToken(TokenTypes.TYPE);
        if (type == null
                || type.findFirstToken(TokenTypes.ARRAY_DECLARATOR) != null
                || variable.findFirstToken(TokenTypes.ELLIPSIS) != null) {
            return null;
        }
        DetailAST first = type.getFirstChild();
        if (first != null && first.getType() == TokenTypes.DOT) {
            first = first.getLastChild().getType() == TokenTypes.TYPE_ARGUMENTS
                    ? first.getLastChild().getPreviousSibling()
                    : first.getLastChild();
        }
        if (first == null
                || first.getType() != TokenTypes.IDENT
                || first.getText().equals("var")) {
            return null;
        }
        return first.getText();
    }

    private static boolean isConstant(DetailAST variable) {
        DetailAST modifiers = variable.findFirstToken(TokenTypes.MODIFIERS);
        return modifiers != null
                && modifiers.findFirstToken(TokenTypes.LITERAL_STATIC) != null
                && modifiers.findFirstToken(TokenTypes.FINAL) != null;
    }

    private static boolean hasQualifier(String name, String typeName) {
        return name.length() > typeName.length() && name.endsWith(typeName) && Character.isLowerCase(name.charAt(0));
    }

    /** Leading capitals lowered, the last one kept when a lower-case letter follows: {@code AIService} is {@code aiService}. */
    private static String lowerCamelCase(String typeName) {
        int capitals = 0;
        while (capitals < typeName.length() && Character.isUpperCase(typeName.charAt(capitals))) {
            capitals++;
        }
        if (capitals > 1
                && LOWER_CASE_START.matcher(typeName.substring(capitals)).matches()) {
            capitals--;
        }
        return typeName.substring(0, capitals).toLowerCase(java.util.Locale.ROOT) + typeName.substring(capitals);
    }

    private String idPrefix() {
        return getId() == null ? "" : getId() + ": ";
    }
}
