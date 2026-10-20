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

class StaticMethodInAnnotatedClassCheckTest {

    private static final String BASIC_FIXTURE = "Basic.java";

    @ParameterizedTest(name = "{0} on {1}")
    @CsvSource(delimiter = '|', textBlock = """
                    Marked, Other                           | Basic.java
                    com.example.Marked, com.example.Other   | Basic.java
                    com.example.Marked                      | QualifiedImported.java
                    com.example.Marked                      | QualifiedOnDemand.java
                    com.example.Marked                      | OtherImport.java
                    """)
    void reportsExactlyTheMarkedLines(String annotations, String fixtureName) throws Exception {
        Path fixture = fixture(fixtureName);

        List<Finding> findings = CheckstyleRunner.run(
                StaticMethodInAnnotatedClassCheck.class, Map.of("annotations", annotations), fixture);

        assertThat(findings).extracting(Finding::line).containsExactlyElementsOf(CheckstyleRunner.markedLines(fixture));
    }

    @Test
    void aSimpleNameDoesNotKnowWhichAnnotationTheCodeMeans() throws Exception {
        List<Finding> findings = CheckstyleRunner.run(
                StaticMethodInAnnotatedClassCheck.class, Map.of("annotations", "Marked"), fixture("OtherImport.java"));

        assertThat(findings).extracting(Finding::line).containsExactly(7);
    }

    @Test
    void aClassWithoutTheConfiguredAnnotationIsNotChecked() throws Exception {
        List<Finding> findings = CheckstyleRunner.run(
                StaticMethodInAnnotatedClassCheck.class, Map.of("annotations", "Other"), fixture(BASIC_FIXTURE));

        assertThat(findings).extracting(Finding::line).containsExactly(49);
    }

    @Test
    void messageNamesTheIdTheMethodTheClassTheAnnotationsAndTheSuggestion() throws Exception {
        Map<String, String> properties = Map.of(
                "id",
                "TAP-X1",
                "annotations",
                "com.example.Marked, com.example.Other",
                "suggestion",
                "Make it an instance method.");

        List<Finding> findings =
                CheckstyleRunner.run(StaticMethodInAnnotatedClassCheck.class, properties, fixture(BASIC_FIXTURE));

        assertThat(findings.getFirst())
                .isEqualTo(new Finding(
                        13,
                        findings.getFirst().column(),
                        "TAP-X1",
                        "TAP-X1: Static method 'helper' in 'FooBean', which is annotated @Marked."
                                + " Make it an instance method."));
        assertThat(findings.getLast().message())
                .isEqualTo("TAP-X1: Static method 'twice' in 'Both', which is annotated @Other, @Marked."
                        + " Make it an instance method.");
    }

    @Test
    void messageWithoutIdAndSuggestionIsTheSentence() throws Exception {
        List<Finding> findings = CheckstyleRunner.run(
                StaticMethodInAnnotatedClassCheck.class, Map.of("annotations", "Marked"), fixture(BASIC_FIXTURE));

        assertThat(findings.getFirst().id()).isNull();
        assertThat(findings.getFirst().message())
                .isEqualTo("Static method 'helper' in 'FooBean', which is annotated @Marked.");
    }

    @Test
    void stateDoesNotLeakFromOneFileToTheNext() throws Exception {
        var configuration = CheckstyleRunner.checkerWith(
                StaticMethodInAnnotatedClassCheck.class, Map.of("annotations", "com.example.Marked"));

        List<Finding> findings =
                CheckstyleRunner.run(configuration, fixture("QualifiedOnDemand.java"), fixture("OtherImport.java"));

        assertThat(findings).extracting(Finding::line).containsExactly(7);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Foo..Bar", "*Marked", "1Marked", "@Marked", "com.example.Marked."})
    void invalidAnnotationIsRejectedWithItsText(String type) {
        StaticMethodInAnnotatedClassCheck check = new StaticMethodInAnnotatedClassCheck();

        assertThatThrownBy(() -> check.setAnnotations("Marked", type))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("'" + type + "' is not a Java type name");
    }

    @Test
    void invalidValueFailsTheConfiguration() {
        assertThatThrownBy(() -> CheckstyleRunner.run(
                        StaticMethodInAnnotatedClassCheck.class,
                        Map.of("annotations", "Foo..Bar"),
                        fixture(BASIC_FIXTURE)))
                .isInstanceOf(CheckstyleException.class)
                .hasStackTraceContaining("'Foo..Bar' is not a Java type name");
    }

    @Test
    void annotationsAreRequired() {
        StaticMethodInAnnotatedClassCheck check = new StaticMethodInAnnotatedClassCheck();

        assertThatThrownBy(check::finishLocalSetup)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("StaticMethodInAnnotatedClassCheck: 'annotations' is required");
    }

    @Test
    void missingAnnotationsFailTheConfiguration() {
        assertThatThrownBy(() ->
                        CheckstyleRunner.run(StaticMethodInAnnotatedClassCheck.class, Map.of(), fixture(BASIC_FIXTURE)))
                .hasStackTraceContaining("'annotations' is required");
    }

    @Test
    void registersTheTokensItNeeds() {
        StaticMethodInAnnotatedClassCheck check = new StaticMethodInAnnotatedClassCheck();
        int[] expected = {TokenTypes.METHOD_DEF};

        assertThat(check.getDefaultTokens()).containsExactly(expected);
        assertThat(check.getAcceptableTokens()).containsExactly(expected);
        assertThat(check.getRequiredTokens()).containsExactly(expected);
    }

    private static Path fixture(String name) {
        try {
            return Path.of(StaticMethodInAnnotatedClassCheckTest.class
                    .getResource("/checks/StaticMethodInAnnotatedClassCheck/" + name)
                    .toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
