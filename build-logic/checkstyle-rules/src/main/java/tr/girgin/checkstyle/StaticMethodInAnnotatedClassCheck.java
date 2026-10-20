package tr.girgin.checkstyle;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import tr.girgin.checkstyle.support.Imports;
import tr.girgin.checkstyle.support.SourceNames;
import tr.girgin.checkstyle.support.TypeName;

/**
 * Reports a static method declared in a class that carries one of the configured annotations. The annotations
 * are configured with {@code annotations}; see
 * build-logic/checkstyle-rules/docs/StaticMethodInAnnotatedClassCheck.md.
 */
public class StaticMethodInAnnotatedClassCheck extends AbstractCheck {

    public static final String MSG_STATIC = "static.method.in.annotated.class";

    private List<TypeName> annotations = List.of();
    private String suggestion = "";
    private Imports imports;

    /** The annotation types, simple or fully qualified, of the classes whose methods must not be static. */
    public void setAnnotations(String... types) {
        annotations = Arrays.stream(types).map(TypeName::parse).toList();
    }

    /** Appended to every message: how to fix the finding. */
    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    @Override
    protected void finishLocalSetup() {
        if (annotations.isEmpty()) {
            throw new IllegalArgumentException("StaticMethodInAnnotatedClassCheck: 'annotations' is required");
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
        return new int[] {TokenTypes.METHOD_DEF};
    }

    @Override
    public void beginTree(DetailAST rootAST) {
        imports = Imports.of(rootAST);
    }

    @Override
    public void visitToken(DetailAST method) {
        DetailAST owner = method.getParent().getParent();
        if (owner.getType() != TokenTypes.CLASS_DEF
                || method.findFirstToken(TokenTypes.MODIFIERS).findFirstToken(TokenTypes.LITERAL_STATIC) == null) {
            return;
        }
        List<String> matched = matchedAnnotations(owner);
        if (matched.isEmpty()) {
            return;
        }
        DetailAST name = method.findFirstToken(TokenTypes.IDENT);
        log(
                name,
                MSG_STATIC,
                idPrefix(),
                name.getText(),
                owner.findFirstToken(TokenTypes.IDENT).getText(),
                matched.stream().map(written -> "@" + written).collect(Collectors.joining(", ")),
                suggestionSuffix());
    }

    private List<String> matchedAnnotations(DetailAST owner) {
        DetailAST modifiers = owner.findFirstToken(TokenTypes.MODIFIERS);
        List<String> matched = new ArrayList<>();
        for (DetailAST child = modifiers.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getType() == TokenTypes.ANNOTATION) {
                String written =
                        SourceNames.of(child.findFirstToken(TokenTypes.AT).getNextSibling());
                if (annotations.stream().anyMatch(type -> type.matches(written, imports))) {
                    matched.add(SourceNames.lastSegment(written));
                }
            }
        }
        return matched;
    }

    /** {@code TAP-D7: } for a module with an {@code id}, nothing without. */
    private String idPrefix() {
        return getId() == null ? "" : getId() + ": ";
    }

    private String suggestionSuffix() {
        return suggestion.isEmpty() ? "" : " " + suggestion;
    }
}
