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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tr.girgin.checkstyle.CheckstyleRunner.Finding;

class RequiredAnnotationAttributeCheckTest {

    private static final String BASIC_FIXTURE = "Basic.java";

    @ParameterizedTest(name = "{0} on {1}")
    @CsvSource(delimiter = '|', textBlock = """
                    Marked                | Basic.java
                    com.example.Marked    | Basic.java
                    com.example.Marked    | Qualified.java
                    com.example.Marked    | QualifiedOnDemand.java
                    com.example.Marked    | OtherImport.java
                    """)
    void reportsExactlyTheMarkedLines(String annotations, String fixtureName) throws Exception {
        Path fixture = fixture(fixtureName);

        List<Finding> findings = CheckstyleRunner.run(
                RequiredAnnotationAttributeCheck.class,
                Map.of("annotations", annotations, "attributes", "config"),
                fixture);

        assertThat(findings).extracting(Finding::line).containsExactlyElementsOf(CheckstyleRunner.markedLines(fixture));
    }

    @Test
    void aSingleElementAnnotationSetsValue() throws Exception {
        List<Finding> findings = CheckstyleRunner.run(
                RequiredAnnotationAttributeCheck.class,
                Map.of("annotations", "Marked", "attributes", "value"),
                fixture(BASIC_FIXTURE));

        assertThat(findings).extracting(Finding::line).containsExactly(6, 9, 12, 15, 27, 34, 37);
    }

    @Test
    void everyMissingAttributeIsReported() throws Exception {
        List<Finding> findings = CheckstyleRunner.run(
                RequiredAnnotationAttributeCheck.class,
                Map.of("annotations", "Marked", "attributes", "config, name"),
                fixture(BASIC_FIXTURE));

        assertThat(findings)
                .extracting(Finding::line)
                .containsExactlyInAnyOrder(6, 6, 9, 12, 18, 18, 21, 21, 24, 24, 27, 27, 34, 34, 37, 37);
        assertThat(findings)
                .filteredOn(finding -> finding.line() == 9)
                .extracting(Finding::message)
                .containsExactly("Annotation '@Marked' does not set 'config'.");
        assertThat(findings)
                .filteredOn(finding -> finding.line() == 12)
                .extracting(Finding::message)
                .containsExactly("Annotation '@Marked' does not set 'name'.");
    }

    @Test
    void messageNamesTheIdTheAnnotationAsWrittenTheAttributeAndTheSuggestion() throws Exception {
        Map<String, String> properties = Map.of(
                "id", "TAP-X1", "annotations", "com.example.Marked", "attributes", "config", "suggestion", "Set it.");

        List<Finding> findings =
                CheckstyleRunner.run(RequiredAnnotationAttributeCheck.class, properties, fixture("Qualified.java"));

        assertThat(findings.getFirst())
                .isEqualTo(new Finding(
                        5,
                        findings.getFirst().column(),
                        "TAP-X1",
                        "TAP-X1: Annotation '@Marked' does not set 'config'. Set it."));
        assertThat(findings.getLast().message())
                .isEqualTo("TAP-X1: Annotation '@com.example.Marked' does not set 'config'. Set it.");
    }

    @Test
    void messageWithoutIdAndSuggestionIsTheSentence() throws Exception {
        List<Finding> findings = CheckstyleRunner.run(
                RequiredAnnotationAttributeCheck.class,
                Map.of("annotations", "Marked", "attributes", "config"),
                fixture(BASIC_FIXTURE));

        assertThat(findings.getFirst().id()).isNull();
        assertThat(findings.getFirst().message()).isEqualTo("Annotation '@Marked' does not set 'config'.");
    }

    @Test
    void stateDoesNotLeakFromOneFileToTheNext() throws Exception {
        var configuration = CheckstyleRunner.checkerWith(
                RequiredAnnotationAttributeCheck.class,
                Map.of("annotations", "com.example.Marked", "attributes", "config"));

        List<Finding> findings =
                CheckstyleRunner.run(configuration, fixture("QualifiedOnDemand.java"), fixture("OtherImport.java"));

        assertThat(findings).extracting(Finding::line).containsExactly(5);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Foo..Bar", "*Marked", "1Marked", "@Marked", "com.example.Marked."})
    void invalidAnnotationIsRejectedWithItsText(String type) {
        RequiredAnnotationAttributeCheck check = new RequiredAnnotationAttributeCheck();

        assertThatThrownBy(() -> check.setAnnotations("Marked", type))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("'" + type + "' is not a Java type name");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a.b", "1a", "a b", "*"})
    void invalidAttributeIsRejectedWithItsText(String name) {
        RequiredAnnotationAttributeCheck check = new RequiredAnnotationAttributeCheck();

        assertThatThrownBy(() -> check.setAttributes("config", name))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("'" + name + "' is not a Java identifier");
    }

    @Test
    void invalidValueFailsTheConfiguration() {
        assertThatThrownBy(() -> CheckstyleRunner.run(
                        RequiredAnnotationAttributeCheck.class,
                        Map.of("annotations", "Marked", "attributes", "a.b"),
                        fixture(BASIC_FIXTURE)))
                .isInstanceOf(CheckstyleException.class)
                .hasStackTraceContaining("'a.b' is not a Java identifier");
    }

    @Test
    void annotationsAreRequired() {
        RequiredAnnotationAttributeCheck check = new RequiredAnnotationAttributeCheck();
        check.setAttributes("config");

        assertThatThrownBy(check::finishLocalSetup)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("RequiredAnnotationAttributeCheck: 'annotations' is required");
    }

    @Test
    void attributesAreRequired() {
        RequiredAnnotationAttributeCheck check = new RequiredAnnotationAttributeCheck();
        check.setAnnotations("Marked");

        assertThatThrownBy(check::finishLocalSetup)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("RequiredAnnotationAttributeCheck: 'attributes' is required");
    }

    @Test
    void missingParametersFailTheConfiguration() {
        assertThatThrownBy(() -> CheckstyleRunner.run(
                        RequiredAnnotationAttributeCheck.class, Map.of("attributes", "config"), fixture(BASIC_FIXTURE)))
                .hasStackTraceContaining("'annotations' is required");
    }

    @Test
    void registersTheTokensItNeeds() {
        RequiredAnnotationAttributeCheck check = new RequiredAnnotationAttributeCheck();
        int[] expected = {TokenTypes.ANNOTATION};

        assertThat(check.getDefaultTokens()).containsExactly(expected);
        assertThat(check.getAcceptableTokens()).containsExactly(expected);
        assertThat(check.getRequiredTokens()).containsExactly(expected);
    }

    private static Path fixture(String name) {
        try {
            return Path.of(RequiredAnnotationAttributeCheckTest.class
                    .getResource("/checks/RequiredAnnotationAttributeCheck/" + name)
                    .toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
