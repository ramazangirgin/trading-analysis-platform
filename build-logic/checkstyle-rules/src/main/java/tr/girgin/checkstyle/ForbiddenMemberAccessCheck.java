package tr.girgin.checkstyle;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import tr.girgin.checkstyle.support.Imports;
import tr.girgin.checkstyle.support.MemberPattern;
import tr.girgin.checkstyle.support.SourceNames;

/**
 * Reports a reference to a forbidden field, a call of a forbidden method or a call of a forbidden constructor.
 * What is forbidden is configured with {@code members}; see
 * build-logic/checkstyle-rules/docs/ForbiddenMemberAccessCheck.md.
 */
public class ForbiddenMemberAccessCheck extends AbstractCheck {

    /** Message key of a forbidden field. */
    public static final String MSG_FIELD = "forbidden.member.field";

    /** Message key of a forbidden method. */
    public static final String MSG_METHOD = "forbidden.member.method";

    /** Message key of a forbidden constructor. */
    public static final String MSG_CONSTRUCTOR = "forbidden.member.constructor";

    private List<MemberPattern> members = List.of();
    private String suggestion = "";
    private Imports imports;

    /** The forbidden members, one pattern each: {@code Type#field}, {@code Type#method()}, {@code Type#new()}. */
    public void setMembers(String... patterns) {
        members = Arrays.stream(patterns).map(MemberPattern::parse).toList();
    }

    /** Appended to every message: how to fix the finding. */
    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    @Override
    protected void finishLocalSetup() {
        if (members.isEmpty()) {
            throw new IllegalArgumentException("ForbiddenMemberAccessCheck: 'members' is required");
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
        return new int[] {TokenTypes.DOT, TokenTypes.METHOD_CALL, TokenTypes.LITERAL_NEW};
    }

    @Override
    public void beginTree(DetailAST rootAST) {
        imports = Imports.of(rootAST);
    }

    @Override
    public void visitToken(DetailAST ast) {
        switch (ast.getType()) {
            case TokenTypes.DOT -> visitFieldAccess(ast);
            case TokenTypes.METHOD_CALL -> visitMethodCall(ast);
            default -> visitConstructorCall(ast);
        }
    }

    private void visitFieldAccess(DetailAST dot) {
        boolean callee = dot.getParent().getType() == TokenTypes.METHOD_CALL;
        if (callee || dot.getLastChild().getType() != TokenTypes.IDENT || SourceNames.inDeclarationHeader(dot)) {
            return;
        }
        String receiver = SourceNames.of(dot.getFirstChild());
        String field = dot.getLastChild().getText();
        report(dot, MSG_FIELD, pattern -> pattern.matchesField(receiver, field, imports));
    }

    private void visitMethodCall(DetailAST call) {
        DetailAST callee = call.getFirstChild();
        if (callee.getType() != TokenTypes.DOT) {
            return;
        }
        String receiver = SourceNames.of(callee.getFirstChild());
        String method = callee.getLastChild().getText();
        int arguments = argumentCount(call);
        report(callee, MSG_METHOD, pattern -> pattern.matchesMethod(receiver, method, arguments, imports));
    }

    private void visitConstructorCall(DetailAST creation) {
        // new int[3] and new Foo[3] have no argument list.
        if (creation.findFirstToken(TokenTypes.ELIST) == null) {
            return;
        }
        String type = SourceNames.of(creation.getFirstChild());
        int arguments = argumentCount(creation);
        report(creation, MSG_CONSTRUCTOR, pattern -> pattern.matchesConstructor(type, arguments, imports));
    }

    private static int argumentCount(DetailAST callOrCreation) {
        return callOrCreation.findFirstToken(TokenTypes.ELIST).getChildCount(TokenTypes.EXPR);
    }

    /** Reports once, for the first pattern that selects the node. */
    private void report(DetailAST at, String key, Predicate<MemberPattern> selects) {
        members.stream()
                .filter(selects)
                .findFirst()
                .ifPresent(found -> log(at, key, idPrefix(), found.source(), suggestionSuffix()));
    }

    /** {@code TAP-L1: } for a module with an {@code id}, nothing without. */
    private String idPrefix() {
        return getId() == null ? "" : getId() + ": ";
    }

    private String suggestionSuffix() {
        return suggestion.isEmpty() ? "" : " " + suggestion;
    }
}
