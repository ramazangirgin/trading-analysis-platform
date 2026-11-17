package tr.girgin.checkstyle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tr.girgin.checkstyle.CheckstyleRunner.Finding;

class CommentRepeatsCodeCheckTest {

    private static final Map<String, String> IGNORING_ARTICLES = Map.of("ignoredWords", "the, a, an, and");

    @Test
    void reportsExactlyTheMarkedLines() throws Exception {
        Path fixture = fixture("Reported.java");

        List<Finding> findings = CheckstyleRunner.run(CommentRepeatsCodeCheck.class, IGNORING_ARTICLES, fixture);

        assertThat(findings).extracting(Finding::line).containsExactlyElementsOf(CheckstyleRunner.markedLines(fixture));
    }

    @Test
    void reportsNothingOnNearMisses() throws Exception {
        List<Finding> findings =
                CheckstyleRunner.run(CommentRepeatsCodeCheck.class, IGNORING_ARTICLES, fixture("NotReported.java"));

        assertThat(findings).isEmpty();
    }

    @Test
    void aWordOnlyInIgnoredWordsIsNotCounted() throws Exception {
        List<Finding> findings = CheckstyleRunner.run(
                CommentRepeatsCodeCheck.class, Map.of("ignoredWords", "The"), fixture("Ignored.java"));

        assertThat(findings).extracting(Finding::line).containsExactly(8);
    }

    @Test
    void withoutIgnoredWordsEveryWordCounts() throws Exception {
        List<Finding> findings = CheckstyleRunner.run(CommentRepeatsCodeCheck.class, Map.of(), fixture("Ignored.java"));

        assertThat(findings).extracting(Finding::line).containsExactly(5);
    }

    @Test
    void stateDoesNotLeakFromOneFileToTheNext() throws Exception {
        var configuration = CheckstyleRunner.checkerWith(CommentRepeatsCodeCheck.class, Map.of());

        List<Finding> findings = CheckstyleRunner.run(
                configuration, fixture("Ignored.java"), fixture("NoComments.java"), fixture("NotReported.java"));

        assertThat(findings).extracting(Finding::line).containsExactly(5);
    }

    @Test
    void messageNamesTheIdAndTheSuggestion() throws Exception {
        Map<String, String> properties =
                Map.of("id", "TAP-X1", "suggestion", "Say why, or delete it.", "ignoredWords", "the");

        List<Finding> findings =
                CheckstyleRunner.run(CommentRepeatsCodeCheck.class, properties, fixture("Ignored.java"));

        assertThat(findings)
                .containsExactly(new Finding(
                        8,
                        findings.getFirst().column(),
                        "TAP-X1",
                        "TAP-X1: Comment only repeats the words of the code below it. Say why, or delete it."));
    }

    @Test
    void messageWithoutIdAndSuggestionIsTheSentence() throws Exception {
        List<Finding> findings = CheckstyleRunner.run(CommentRepeatsCodeCheck.class, Map.of(), fixture("Ignored.java"));

        assertThat(findings.getFirst().id()).isNull();
        assertThat(findings.getFirst().message()).isEqualTo("Comment only repeats the words of the code below it.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a1", "a b", "a-b", "*"})
    void invalidIgnoredWordIsRejectedWithItsText(String word) {
        CommentRepeatsCodeCheck check = new CommentRepeatsCodeCheck();

        assertThatThrownBy(() -> check.setIgnoredWords("the", word))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("'" + word + "' is not a word of letters");
    }

    @Test
    void invalidValueFailsTheConfiguration() {
        assertThatThrownBy(() -> CheckstyleRunner.run(
                        CommentRepeatsCodeCheck.class, Map.of("ignoredWords", "a1"), fixture("Ignored.java")))
                .isInstanceOf(CheckstyleException.class)
                .hasStackTraceContaining("'a1' is not a word of letters");
    }

    @Test
    void registersTheTokensItNeeds() {
        CommentRepeatsCodeCheck check = new CommentRepeatsCodeCheck();
        int[] expected = {TokenTypes.SINGLE_LINE_COMMENT};

        assertThat(check.getDefaultTokens()).containsExactly(expected);
        assertThat(check.getAcceptableTokens()).containsExactly(expected);
        assertThat(check.getRequiredTokens()).containsExactly(expected);
        assertThat(check.isCommentNodesRequired()).isTrue();
    }

    private static Path fixture(String name) {
        try {
            return Path.of(CommentRepeatsCodeCheckTest.class
                    .getResource("/checks/CommentRepeatsCodeCheck/" + name)
                    .toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
