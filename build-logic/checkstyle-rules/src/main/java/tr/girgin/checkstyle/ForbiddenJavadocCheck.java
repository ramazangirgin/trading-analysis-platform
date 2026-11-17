package tr.girgin.checkstyle;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;

/**
 * Reports every Javadoc comment, wherever it is. See
 * build-logic/checkstyle-rules/docs/ForbiddenJavadocCheck.md.
 */
public class ForbiddenJavadocCheck extends AbstractCheck {

    public static final String MSG_JAVADOC = "forbidden.javadoc";

    private String suggestion = "";

    /** Appended to every message: how to fix the finding. */
    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    @Override
    public boolean isCommentNodesRequired() {
        return true;
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
        return new int[] {TokenTypes.BLOCK_COMMENT_BEGIN};
    }

    @Override
    public void visitToken(DetailAST comment) {
        // The text between "/*" and "*/": a Javadoc starts with a second asterisk, "/**/" has no text.
        if (comment.getFirstChild().getText().startsWith("*")) {
            log(comment, MSG_JAVADOC, idPrefix(), suggestionSuffix());
        }
    }

    /** {@code TAP-J1: } for a module with an {@code id}, nothing without. */
    private String idPrefix() {
        return getId() == null ? "" : getId() + ": ";
    }

    private String suggestionSuffix() {
        return suggestion.isEmpty() ? "" : " " + suggestion;
    }
}
