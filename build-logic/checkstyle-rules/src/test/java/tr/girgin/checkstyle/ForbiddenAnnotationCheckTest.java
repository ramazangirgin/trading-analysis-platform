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

class ForbiddenAnnotationCheckTest {

    private static final String BASIC_FIXTURE = "Basic.java";
    private static final String ATTRIBUTES_FIXTURE = "Attributes.java";

    @ParameterizedTest(name = "{0} on {1}")
    @CsvSource(delimiter = '|', textBlock = """
                    Marked                | Basic.java
                    com.example.Marked    | Basic.java
                    com.example.Marked    | Qualified.java
                    com.example.Marked    | QualifiedOnDemand.java
                    com.example.Marked    | SamePackage.java
                    com.example.Marked    | OtherImport.java
                    """)
    void reportsEveryUseOfTheAnnotationOnTheMarkedLines(String annotations, String fixtureName) throws Exception {
        Path fixture = fixture(fixtureName);

        List<Finding> findings =
                CheckstyleRunner.run(ForbiddenAnnotationCheck.class, Map.of("annotations", annotations), fixture);

        assertThat(findings).extracting(Finding::line).containsExactlyElementsOf(CheckstyleRunner.markedLines(fixture));
    }

    @Test
    void withAttributesOnlyAUseThatSetsOneIsReported() throws Exception {
        Path fixture = fixture(ATTRIBUTES_FIXTURE);

        List<Finding> findings = CheckstyleRunner.run(
                ForbiddenAnnotationCheck.class,
                Map.of("annotations", "Marked", "attributes", "expression, defaultExpression"),
                fixture);

        assertThat(findings).extracting(Finding::line).containsExactlyElementsOf(CheckstyleRunner.markedLines(fixture));
    }

    @Test
    void everyForbiddenAttributeThatIsSetIsReportedOnce() throws Exception {
        List<Finding> findings = CheckstyleRunner.run(
                ForbiddenAnnotationCheck.class,
                Map.of("annotations", "Marked", "attributes", "name, expression"),
                fixture(ATTRIBUTES_FIXTURE));

        assertThat(findings).extracting(Finding::line).containsExactly(9, 12, 15, 15, 32, 33, 38);
        assertThat(findings)
                .filteredOn(finding -> finding.line() == 15)
                .extracting(Finding::message)
                .containsExactlyInAnyOrder(
                        "Annotation '@Marked' must not set 'name'.", "Annotation '@Marked' must not set 'expression'.");
    }

    @Test
    void aSingleElementAnnotationSetsValue() throws Exception {
        List<Finding> findings = CheckstyleRunner.run(
                ForbiddenAnnotationCheck.class,
                Map.of("annotations", "Marked", "attributes", "value"),
                fixture(ATTRIBUTES_FIXTURE));

        assertThat(findings).extracting(Finding::line).containsExactly(18, 21);
    }

    @Test
    void anAnnotationNestedInAnotherIsCheckedToo() throws Exception {
        List<Finding> findings = CheckstyleRunner.run(
                ForbiddenAnnotationCheck.class,
                Map.of("annotations", "Marks, Marked", "attributes", "value"),
                fixture(ATTRIBUTES_FIXTURE));

        assertThat(findings).extracting(Finding::line).contains(18, 21, 31);
    }

    @Test
    void messageWithIdAndSuggestionForTheAnnotation() throws Exception {
        Map<String, String> properties =
                Map.of("id", "TAP-X1", "annotations", "com.example.Marked", "suggestion", "Remove it.");

        List<Finding> findings =
                CheckstyleRunner.run(ForbiddenAnnotationCheck.class, properties, fixture("Qualified.java"));

        assertThat(findings.getFirst())
                .isEqualTo(new Finding(
                        5,
                        findings.getFirst().column(),
                        "TAP-X1",
                        "TAP-X1: Annotation '@Marked' is not allowed. Remove it."));
        assertThat(findings.getLast().message())
                .isEqualTo("TAP-X1: Annotation '@com.example.Marked' is not allowed. Remove it.");
    }

    @Test
    void messageWithIdAndSuggestionForAnAttribute() throws Exception {
        Map<String, String> properties = Map.of(
                "id", "TAP-X2", "annotations", "Marked", "attributes", "expression", "suggestion", "Use source.");

        List<Finding> findings =
                CheckstyleRunner.run(ForbiddenAnnotationCheck.class, properties, fixture(ATTRIBUTES_FIXTURE));

        assertThat(findings.getFirst().id()).isEqualTo("TAP-X2");
        assertThat(findings.getFirst().message())
                .isEqualTo("TAP-X2: Annotation '@Marked' must not set 'expression'. Use source.");
    }

    @Test
    void messageWithoutIdAndSuggestionIsTheSentence() throws Exception {
        List<Finding> findings = CheckstyleRunner.run(
                ForbiddenAnnotationCheck.class, Map.of("annotations", "Marked"), fixture(BASIC_FIXTURE));

        assertThat(findings.getFirst().id()).isNull();
        assertThat(findings.getFirst().message()).isEqualTo("Annotation '@Marked' is not allowed.");
    }

    @Test
    void stateDoesNotLeakFromOneFileToTheNext() throws Exception {
        var configuration = CheckstyleRunner.checkerWith(
                ForbiddenAnnotationCheck.class, Map.of("annotations", "com.example.Marked"));

        List<Finding> findings =
                CheckstyleRunner.run(configuration, fixture("QualifiedOnDemand.java"), fixture("OtherImport.java"));

        assertThat(findings).extracting(Finding::line).containsExactly(5);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Foo..Bar", "*Marked", "1Marked", "@Marked", "com.example.Marked."})
    void invalidAnnotationIsRejectedWithItsText(String type) {
        ForbiddenAnnotationCheck check = new ForbiddenAnnotationCheck();

        assertThatThrownBy(() -> check.setAnnotations("Marked", type))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("'" + type + "' is not a Java type name");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a.b", "1a", "a b", "*"})
    void invalidAttributeIsRejectedWithItsText(String name) {
        ForbiddenAnnotationCheck check = new ForbiddenAnnotationCheck();

        assertThatThrownBy(() -> check.setAttributes("expression", name))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("'" + name + "' is not a Java identifier");
    }

    @Test
    void invalidValueFailsTheConfiguration() {
        assertThatThrownBy(() -> CheckstyleRunner.run(
                        ForbiddenAnnotationCheck.class,
                        Map.of("annotations", "Marked", "attributes", "a.b"),
                        fixture(BASIC_FIXTURE)))
                .isInstanceOf(CheckstyleException.class)
                .hasStackTraceContaining("'a.b' is not a Java identifier");
    }

    @Test
    void annotationsAreRequired() {
        ForbiddenAnnotationCheck check = new ForbiddenAnnotationCheck();

        assertThatThrownBy(check::finishLocalSetup)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ForbiddenAnnotationCheck: 'annotations' is required");
    }

    @Test
    void missingAnnotationsFailTheConfiguration() {
        assertThatThrownBy(() -> CheckstyleRunner.run(
                        ForbiddenAnnotationCheck.class, Map.of("attributes", "expression"), fixture(BASIC_FIXTURE)))
                .hasStackTraceContaining("'annotations' is required");
    }

    @Test
    void registersTheTokensItNeeds() {
        ForbiddenAnnotationCheck check = new ForbiddenAnnotationCheck();
        int[] expected = {TokenTypes.ANNOTATION};

        assertThat(check.getDefaultTokens()).containsExactly(expected);
        assertThat(check.getAcceptableTokens()).containsExactly(expected);
        assertThat(check.getRequiredTokens()).containsExactly(expected);
    }

    private static Path fixture(String name) {
        try {
            return Path.of(ForbiddenAnnotationCheckTest.class
                    .getResource("/checks/ForbiddenAnnotationCheck/" + name)
                    .toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
