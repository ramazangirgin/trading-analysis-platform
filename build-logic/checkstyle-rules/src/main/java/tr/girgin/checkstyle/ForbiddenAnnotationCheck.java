package tr.girgin.checkstyle;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import tr.girgin.checkstyle.support.AnnotationAttributes;
import tr.girgin.checkstyle.support.Identifiers;
import tr.girgin.checkstyle.support.Imports;
import tr.girgin.checkstyle.support.SourceNames;
import tr.girgin.checkstyle.support.TypeName;

/**
 * Reports a use of a forbidden annotation, or a use of it that sets a forbidden attribute. The annotations and
 * attributes are configured with {@code annotations} and {@code attributes}; see
 * build-logic/checkstyle-rules/docs/ForbiddenAnnotationCheck.md.
 */
public class ForbiddenAnnotationCheck extends AbstractCheck {

    public static final String MSG_ANNOTATION = "forbidden.annotation";
    public static final String MSG_ATTRIBUTE = "forbidden.annotation.attribute";

    private List<TypeName> annotations = List.of();
    private List<String> attributes = List.of();
    private String suggestion = "";
    private Imports imports;

    /** The annotation types, simple or fully qualified, that are forbidden (or whose attributes are). */
    public void setAnnotations(String... types) {
        annotations = Arrays.stream(types).map(TypeName::parse).toList();
    }

    /** The attributes that are forbidden on those annotations; without any, the annotations themselves are. */
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
            throw new IllegalArgumentException("ForbiddenAnnotationCheck: 'annotations' is required");
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
        if (attributes.isEmpty()) {
            log(annotation, MSG_ANNOTATION, idPrefix(), written, suggestionSuffix());
            return;
        }
        Set<String> set = AnnotationAttributes.of(annotation);
        for (String forbidden : attributes) {
            if (set.contains(forbidden)) {
                log(annotation, MSG_ATTRIBUTE, idPrefix(), written, forbidden, suggestionSuffix());
            }
        }
    }

    /** {@code TAP-M2: } for a module with an {@code id}, nothing without. */
    private String idPrefix() {
        return getId() == null ? "" : getId() + ": ";
    }

    private String suggestionSuffix() {
        return suggestion.isEmpty() ? "" : " " + suggestion;
    }
}
