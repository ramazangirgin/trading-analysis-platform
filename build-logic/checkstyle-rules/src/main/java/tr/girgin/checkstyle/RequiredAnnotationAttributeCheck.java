package tr.girgin.checkstyle;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import tr.girgin.checkstyle.support.Identifiers;
import tr.girgin.checkstyle.support.Imports;
import tr.girgin.checkstyle.support.SourceNames;
import tr.girgin.checkstyle.support.TypeName;

/**
 * Reports an annotation of one of the configured types that does not set every required attribute. The types
 * and attributes are configured with {@code annotations} and {@code attributes}; see
 * build-logic/checkstyle-rules/docs/RequiredAnnotationAttributeCheck.md.
 */
public class RequiredAnnotationAttributeCheck extends AbstractCheck {

    public static final String MSG_MISSING = "required.annotation.attribute";

    private static final String VALUE = "value";

    private List<TypeName> annotations = List.of();
    private List<String> attributes = List.of();
    private String suggestion = "";
    private Imports imports;

    /** The annotation types, simple or fully qualified, that must set the required attributes. */
    public void setAnnotations(String... types) {
        annotations = Arrays.stream(types).map(TypeName::parse).toList();
    }

    /** The names of the attributes every configured annotation must set. */
    public void setAttributes(String... names) {
        for (String name : names) {
            if (!Identifiers.isIdentifier(name)) {
                throw new IllegalArgumentException("'" + name + "' is not a Java identifier");
            }
        }
        attributes = List.of(names);
    }

    /** Appended to every message: how to fix the finding. */
    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    @Override
    protected void finishLocalSetup() {
        if (annotations.isEmpty()) {
            throw new IllegalArgumentException("RequiredAnnotationAttributeCheck: 'annotations' is required");
        }
        if (attributes.isEmpty()) {
            throw new IllegalArgumentException("RequiredAnnotationAttributeCheck: 'attributes' is required");
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
        return new int[] {TokenTypes.ANNOTATION};
    }

    @Override
    public void beginTree(DetailAST rootAST) {
        imports = Imports.of(rootAST);
    }

    @Override
    public void visitToken(DetailAST annotation) {
        String written = SourceNames.of(annotation.findFirstToken(TokenTypes.AT).getNextSibling());
        if (annotations.stream().noneMatch(type -> type.matches(written, imports))) {
            return;
        }
        Set<String> set = setAttributes(annotation);
        for (String required : attributes) {
            if (!set.contains(required)) {
                log(annotation, MSG_MISSING, idPrefix(), written, required, suggestionSuffix());
            }
        }
    }

    private static Set<String> setAttributes(DetailAST annotation) {
        Set<String> names = new HashSet<>();
        for (DetailAST child = annotation.getFirstChild(); child != null; child = child.getNextSibling()) {
            int type = child.getType();
            if (type == TokenTypes.ANNOTATION_MEMBER_VALUE_PAIR) {
                names.add(child.findFirstToken(TokenTypes.IDENT).getText());
            } else if (type == TokenTypes.EXPR
                    || type == TokenTypes.ANNOTATION
                    || type == TokenTypes.ANNOTATION_ARRAY_INIT) {
                names.add(VALUE);
            }
        }
        return names;
    }

    /** {@code TAP-M1: } for a module with an {@code id}, nothing without. */
    private String idPrefix() {
        return getId() == null ? "" : getId() + ": ";
    }

    private String suggestionSuffix() {
        return suggestion.isEmpty() ? "" : " " + suggestion;
    }
}
