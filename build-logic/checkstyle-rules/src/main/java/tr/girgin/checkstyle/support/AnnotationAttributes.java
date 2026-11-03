package tr.girgin.checkstyle.support;

import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import java.util.LinkedHashSet;
import java.util.Set;

/** The attributes an annotation sets. */
public final class AnnotationAttributes {

    private static final String VALUE = "value";

    private AnnotationAttributes() {}

    /** The names of the attributes the annotation sets, in source order; a shorthand value counts as {@code value}. */
    public static Set<String> of(DetailAST annotation) {
        Set<String> names = new LinkedHashSet<>();
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
}
