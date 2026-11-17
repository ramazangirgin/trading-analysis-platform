package tr.girgin.checkstyle;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reports a {@code //} comment on lines of its own whose words all occur in the next statement. See
 * build-logic/checkstyle-rules/docs/CommentRepeatsCodeCheck.md.
 */
public class CommentRepeatsCodeCheck extends AbstractCheck {

    public static final String MSG_REPEATS = "comment.repeats.code";

    private static final Pattern LETTERS_ONLY = Pattern.compile("\\p{L}+");
    private static final Pattern NOT_LETTERS = Pattern.compile("[^\\p{L}]+");
    private static final Pattern CODE_WORD_BOUNDARY =
            Pattern.compile("[^\\p{L}]+|(?<=\\p{Ll})(?=\\p{Lu})|(?<=\\p{Lu})(?=\\p{Lu}\\p{Ll})");
    // Whitespace, comments and literals come first so that their text is not mistaken for code.
    private static final Pattern TOKEN = Pattern.compile(
            "\\s+|//[^\\n]*|/\\*.*?\\*/|\"\"\"(?:\\\\.|[^\\\\])*?\"\"\""
                    + "|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])'|[\\p{L}\\p{N}_$]+|.",
            Pattern.DOTALL);

    private Set<String> ignoredWords = Set.of();
    private String suggestion = "";
    private final Map<Integer, String> ownLineComments = new TreeMap<>();

    /** Words that a comment may use without the code naming them, such as articles. */
    public void setIgnoredWords(String... words) {
        for (String word : words) {
            if (!LETTERS_ONLY.matcher(word).matches()) {
                throw new IllegalArgumentException("'" + word + "' is not a word of letters");
            }
        }
        ignoredWords = Arrays.stream(words).map(CommentRepeatsCodeCheck::lower).collect(Collectors.toSet());
    }

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
        return new int[] {TokenTypes.SINGLE_LINE_COMMENT};
    }

    @Override
    public void beginTree(DetailAST rootAST) {
        ownLineComments.clear();
    }

    @Override
    public void visitToken(DetailAST comment) {
        String before = getLines()[comment.getLineNo() - 1].substring(0, comment.getColumnNo());
        if (before.isBlank()) {
            ownLineComments.put(comment.getLineNo(), comment.getFirstChild().getText());
        }
    }

    @Override
    public void finishTree(DetailAST rootAST) {
        String source = String.join("\n", getLines());
        int[] lineStarts = lineStarts();
        int first = -1;
        int last = -1;
        StringBuilder text = new StringBuilder();
        for (Map.Entry<Integer, String> comment : ownLineComments.entrySet()) {
            if (first != -1 && comment.getKey() != last + 1) {
                check(first, last, text.toString(), source, lineStarts);
                first = -1;
                text.setLength(0);
            }
            first = first == -1 ? comment.getKey() : first;
            last = comment.getKey();
            text.append(' ').append(comment.getValue());
        }
        if (first != -1) {
            check(first, last, text.toString(), source, lineStarts);
        }
    }

    private void check(int firstLine, int lastLine, String comment, String source, int[] lineStarts) {
        Set<String> words = NOT_LETTERS
                .splitAsStream(comment)
                .map(CommentRepeatsCodeCheck::lower)
                .filter(word -> !word.isEmpty() && !ignoredWords.contains(word))
                .collect(Collectors.toSet());
        if (!words.isEmpty() && codeWords(source, lineStarts[lastLine]).containsAll(words)) {
            log(firstLine, MSG_REPEATS, idPrefix(), suggestionSuffix());
        }
    }

    private int[] lineStarts() {
        String[] lines = getLines();
        int[] starts = new int[lines.length + 1];
        for (int index = 0; index < lines.length; index++) {
            starts[index + 1] = starts[index] + lines[index].length() + 1;
        }
        return starts;
    }

    /** The words of the statement that starts at {@code from}, up to its first semicolon or brace. */
    private static Set<String> codeWords(String source, int from) {
        Matcher matcher = TOKEN.matcher(source).region(Math.min(from, source.length()), source.length());
        StringBuilder statement = new StringBuilder();
        while (matcher.lookingAt()) {
            String token = matcher.group();
            matcher.region(matcher.end(), source.length());
            char start = token.charAt(0);
            if (Character.isWhitespace(start)
                    || start == '"'
                    || start == '\''
                    || token.startsWith("/*")
                    || token.startsWith("//")) {
                continue;
            }
            if (start == ';' || start == '{' || start == '}') {
                break;
            }
            statement.append(token).append(' ');
        }
        return Stream.of(CODE_WORD_BOUNDARY.split(statement))
                .map(CommentRepeatsCodeCheck::lower)
                .filter(word -> !word.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
    }

    private static String lower(String word) {
        return word.toLowerCase(Locale.ROOT);
    }

    /** {@code TAP-C1: } for a module with an {@code id}, nothing without. */
    private String idPrefix() {
        return getId() == null ? "" : getId() + ": ";
    }

    private String suggestionSuffix() {
        return suggestion.isEmpty() ? "" : " " + suggestion;
    }
}
