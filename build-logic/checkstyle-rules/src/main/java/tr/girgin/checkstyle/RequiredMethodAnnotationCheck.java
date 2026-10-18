package tr.girgin.checkstyle;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import tr.girgin.checkstyle.support.Imports;
import tr.girgin.checkstyle.support.SimpleNamePattern;
import tr.girgin.checkstyle.support.SourceNames;
import tr.girgin.checkstyle.support.TypeName;

/**
 * Reports a public method of a selected class that carries none of the required annotations. The classes and
 * the annotations are configured with {@code classes} and {@code annotations}; see
 * build-logic/checkstyle-rules/docs/RequiredMethodAnnotationCheck.md.
 */
public class RequiredMethodAnnotationCheck extends AbstractCheck {

    /** Message key of a method without a required annotation. */
    public static final String MSG_MISSING = "required.method.annotation";

    private List<SimpleNamePattern> classes = List.of();
    private List<TypeName> annotations = List.of();
    private String annotationList = "";
    private String suggestion = "";
    private Imports imports;

    /** The classes whose public methods are checked: simple-name patterns such as {@code *ApiDelegateImpl}. */
    public void setClasses(String... patterns) {
        classes = Arrays.stream(patterns).map(SimpleNamePattern::parse).toList();
    }

    /** The annotation types, simple or fully qualified, any one of which satisfies the rule. */
    public void setAnnotations(String... types) {
        annotations = Arrays.stream(types).map(TypeName::parse).toList();
        annotationList = Arrays.stream(types)
                .map(type -> "@" + SourceNames.lastSegment(type))
                .collect(Collectors.joining(", "));
    }

    /** Appended to every message: how to fix the finding. */
    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    @Override
    protected void finishLocalSetup() {
        if (classes.isEmpty()) {
            throw new IllegalArgumentException("RequiredMethodAnnotationCheck: 'classes' is required");
        }
        if (annotations.isEmpty()) {
            throw new IllegalArgumentException("RequiredMethodAnnotationCheck: 'annotations' is required");
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
        if (owner.getType() != TokenTypes.CLASS_DEF || !isPublic(method)) {
            return;
        }
        String className = owner.findFirstToken(TokenTypes.IDENT).getText();
        if (classes.stream().noneMatch(pattern -> pattern.matches(className)) || hasRequiredAnnotation(method)) {
            return;
        }
        DetailAST name = method.findFirstToken(TokenTypes.IDENT);
        log(name, MSG_MISSING, idPrefix(), name.getText(), className, annotationList, suggestionSuffix());
    }

    private static boolean isPublic(DetailAST method) {
        return method.findFirstToken(TokenTypes.MODIFIERS).findFirstToken(TokenTypes.LITERAL_PUBLIC) != null;
    }

    private boolean hasRequiredAnnotation(DetailAST method) {
        DetailAST modifiers = method.findFirstToken(TokenTypes.MODIFIERS);
        for (DetailAST child = modifiers.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getType() == TokenTypes.ANNOTATION && isRequired(child)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRequired(DetailAST annotation) {
        String written = SourceNames.of(annotation.findFirstToken(TokenTypes.AT).getNextSibling());
        return annotations.stream().anyMatch(type -> type.matches(written, imports));
    }

    /** {@code TAP-A1: } for a module with an {@code id}, nothing without. */
    private String idPrefix() {
        return getId() == null ? "" : getId() + ": ";
    }

    private String suggestionSuffix() {
        return suggestion.isEmpty() ? "" : " " + suggestion;
    }
}
