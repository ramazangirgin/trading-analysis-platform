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

class RequiredMethodAnnotationCheckTest {

    private static final String BASIC_FIXTURE = "Basic.java";

    @ParameterizedTest(name = "{0} / {1} on {2}")
    @CsvSource(delimiter = '|', textBlock = """
                    *Delegate                | Required                            | Basic.java
                    *Delegate                | com.example.Required                | QualifiedImported.java
                    *Delegate                | com.example.Required                | QualifiedOnDemand.java
                    *Delegate                | com.example.Required                | QualifiedSamePackage.java
                    *Delegate                | com.example.Required                | OtherImportQualified.java
                    *Delegate                | Required                            | OtherImportSimple.java
                    *Service, *Handler       | com.example.Required, com.example.Other | Multiple.java
                    """)
    void reportsExactlyTheMarkedLines(String classes, String annotations, String fixtureName) throws Exception {
        Path fixture = fixture(fixtureName);

        List<Finding> findings = CheckstyleRunner.run(
                RequiredMethodAnnotationCheck.class, Map.of("classes", classes, "annotations", annotations), fixture);

        assertThat(findings).extracting(Finding::line).containsExactlyElementsOf(CheckstyleRunner.markedLines(fixture));
    }

    @ParameterizedTest(name = "{0} selects {1}")
    @CsvSource(delimiter = '|', textBlock = """
                    *Service           | OrderService UserService
                    Order*             | OrderService OrderController
                    *rder*             | OrderService OrderController
                    AuditHandler       | AuditHandler
                    *                  | OrderService OrderController UserService AuditHandler
                    Order*, *Handler   | OrderService OrderController AuditHandler
                    Missing            |
                    """)
    void selectsTheClassesByPattern(String classes, String expected) throws Exception {
        List<Finding> findings = CheckstyleRunner.run(
                RequiredMethodAnnotationCheck.class,
                Map.of("classes", classes, "annotations", "Required"),
                fixture("Patterns.java"));

        List<String> expectedClasses = expected == null ? List.of() : List.of(expected.split(" "));
        assertThat(findings)
                .extracting(finding -> finding.message().replaceFirst(".* of '(\\w+)' .*", "$1"))
                .containsExactlyInAnyOrderElementsOf(expectedClasses);
    }

    @Test
    void messageNamesTheIdTheMethodTheClassTheAnnotationsAndTheSuggestion() throws Exception {
        Map<String, String> properties = Map.of(
                "id",
                "TAP-X1",
                "classes",
                "*Delegate",
                "annotations",
                "com.example.Required, com.example.Other",
                "suggestion",
                "Declare who may call it.");

        List<Finding> findings =
                CheckstyleRunner.run(RequiredMethodAnnotationCheck.class, properties, fixture(BASIC_FIXTURE));

        assertThat(findings.getFirst())
                .isEqualTo(new Finding(
                        11,
                        findings.getFirst().column(),
                        "TAP-X1",
                        "TAP-X1: Method 'bare' of 'FooDelegate' has none of the annotations"
                                + " @Required, @Other. Declare who may call it."));
    }

    @Test
    void reportsOnTheMethodNameNotOnTheFirstAnnotation() throws Exception {
        List<Finding> findings = CheckstyleRunner.run(
                RequiredMethodAnnotationCheck.class,
                Map.of("classes", "*Delegate", "annotations", "Required"),
                fixture(BASIC_FIXTURE));

        // toString() has @Override on line 13 and its name on line 14.
        assertThat(findings).extracting(Finding::line).contains(14).doesNotContain(13);
    }

    @Test
    void messageWithoutSuggestionEndsAfterTheSentence() throws Exception {
        Map<String, String> properties = Map.of("id", "TAP-X1", "classes", "*Delegate", "annotations", "Required");

        List<Finding> findings =
                CheckstyleRunner.run(RequiredMethodAnnotationCheck.class, properties, fixture(BASIC_FIXTURE));

        assertThat(findings.getFirst().message())
                .isEqualTo("TAP-X1: Method 'bare' of 'FooDelegate' has none of the annotations @Required.");
    }

    @Test
    void messageWithoutIdStartsWithTheSentence() throws Exception {
        Map<String, String> properties = Map.of("classes", "*Delegate", "annotations", "Required");

        List<Finding> findings =
                CheckstyleRunner.run(RequiredMethodAnnotationCheck.class, properties, fixture(BASIC_FIXTURE));

        assertThat(findings.getFirst().id()).isNull();
        assertThat(findings.getFirst().message())
                .isEqualTo("Method 'bare' of 'FooDelegate' has none of the annotations @Required.");
    }

    @Test
    void stateDoesNotLeakFromOneFileToTheNext() throws Exception {
        var configuration = CheckstyleRunner.checkerWith(
                RequiredMethodAnnotationCheck.class,
                Map.of("classes", "*Delegate", "annotations", "com.example.Required"));

        List<Finding> findings = CheckstyleRunner.run(
                configuration, fixture("QualifiedOnDemand.java"), fixture("OtherImportSimple.java"));

        // QualifiedOnDemand imports com.example.*; OtherImportSimple imports org.other.Required and must not inherit.
        assertThat(findings).extracting(Finding::line).containsExactly(11, 8);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Foo.Bar", "A*B", "**", "*A*B*", "1Foo", "Foo Bar", "Foo-*"})
    void invalidClassPatternIsRejectedWithItsText(String pattern) {
        RequiredMethodAnnotationCheck check = new RequiredMethodAnnotationCheck();

        assertThatThrownBy(() -> check.setClasses("*Impl", pattern))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid class name pattern '" + pattern + "': ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Foo..Bar", "*Required", "1Required", "@Required", "com.example.Required."})
    void invalidAnnotationIsRejectedWithItsText(String type) {
        RequiredMethodAnnotationCheck check = new RequiredMethodAnnotationCheck();

        assertThatThrownBy(() -> check.setAnnotations("Required", type))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("'" + type + "' is not a Java type name");
    }

    @Test
    void invalidValueFailsTheConfiguration() {
        assertThatThrownBy(() -> CheckstyleRunner.run(
                        RequiredMethodAnnotationCheck.class,
                        Map.of("classes", "Foo.Bar", "annotations", "Required"),
                        fixture(BASIC_FIXTURE)))
                .isInstanceOf(CheckstyleException.class)
                .hasStackTraceContaining("Invalid class name pattern 'Foo.Bar'");
    }

    @Test
    void classesAreRequired() {
        RequiredMethodAnnotationCheck check = new RequiredMethodAnnotationCheck();
        check.setAnnotations("Required");

        assertThatThrownBy(check::finishLocalSetup)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("RequiredMethodAnnotationCheck: 'classes' is required");
    }

    @Test
    void annotationsAreRequired() {
        RequiredMethodAnnotationCheck check = new RequiredMethodAnnotationCheck();
        check.setClasses("*Impl");

        assertThatThrownBy(check::finishLocalSetup)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("RequiredMethodAnnotationCheck: 'annotations' is required");
    }

    @Test
    void missingParametersFailTheConfiguration() {
        assertThatThrownBy(() -> CheckstyleRunner.run(
                        RequiredMethodAnnotationCheck.class, Map.of("annotations", "Required"), fixture(BASIC_FIXTURE)))
                .hasStackTraceContaining("'classes' is required");
        assertThatThrownBy(() -> CheckstyleRunner.run(
                        RequiredMethodAnnotationCheck.class, Map.of("classes", "*Impl"), fixture(BASIC_FIXTURE)))
                .hasStackTraceContaining("'annotations' is required");
    }

    @Test
    void registersTheTokensItNeeds() {
        RequiredMethodAnnotationCheck check = new RequiredMethodAnnotationCheck();
        int[] expected = {TokenTypes.METHOD_DEF};

        assertThat(check.getDefaultTokens()).containsExactly(expected);
        assertThat(check.getAcceptableTokens()).containsExactly(expected);
        assertThat(check.getRequiredTokens()).containsExactly(expected);
    }

    private static Path fixture(String name) {
        try {
            return Path.of(RequiredMethodAnnotationCheckTest.class
                    .getResource("/checks/RequiredMethodAnnotationCheck/" + name)
                    .toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
