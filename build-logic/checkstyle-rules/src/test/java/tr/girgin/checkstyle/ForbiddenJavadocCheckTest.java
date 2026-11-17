package tr.girgin.checkstyle;

import static org.assertj.core.api.Assertions.assertThat;

import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tr.girgin.checkstyle.CheckstyleRunner.Finding;

class ForbiddenJavadocCheckTest {

    @Test
    void reportsExactlyTheMarkedLines() throws Exception {
        Path fixture = fixture();

        List<Finding> findings = CheckstyleRunner.run(ForbiddenJavadocCheck.class, Map.of(), fixture);

        assertThat(findings).extracting(Finding::line).containsExactlyElementsOf(CheckstyleRunner.markedLines(fixture));
    }

    @Test
    void messageNamesTheIdAndTheSuggestion() throws Exception {
        Map<String, String> properties =
                Map.of("id", "TAP-X1", "suggestion", "Write a // comment that says why, or nothing.");

        List<Finding> findings = CheckstyleRunner.run(ForbiddenJavadocCheck.class, properties, fixture());

        assertThat(findings.getFirst())
                .isEqualTo(new Finding(
                        3,
                        findings.getFirst().column(),
                        "TAP-X1",
                        "TAP-X1: Javadoc is not allowed. Write a // comment that says why, or nothing."));
    }

    @Test
    void messageWithoutIdAndSuggestionIsTheSentence() throws Exception {
        List<Finding> findings = CheckstyleRunner.run(ForbiddenJavadocCheck.class, Map.of(), fixture());

        assertThat(findings.getFirst().id()).isNull();
        assertThat(findings.getFirst().message()).isEqualTo("Javadoc is not allowed.");
    }

    @Test
    void registersTheTokensItNeeds() {
        ForbiddenJavadocCheck check = new ForbiddenJavadocCheck();
        int[] expected = {TokenTypes.BLOCK_COMMENT_BEGIN};

        assertThat(check.getDefaultTokens()).containsExactly(expected);
        assertThat(check.getAcceptableTokens()).containsExactly(expected);
        assertThat(check.getRequiredTokens()).containsExactly(expected);
        assertThat(check.isCommentNodesRequired()).isTrue();
    }

    private static Path fixture() {
        try {
            return Path.of(ForbiddenJavadocCheckTest.class
                    .getResource("/checks/ForbiddenJavadocCheck/Basic.java")
                    .toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
