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

class VariableNamedAfterTypeCheckTest {

    private static final String TYPES = "*Service, *Port";
    private static final String DECLARATIONS = "Declarations.java";
    private static final String QUALIFIER = "Qualifier.java";

    @ParameterizedTest(name = "{0}")
    @CsvSource({"Declarations.java", "NotChecked.java"})
    void reportsExactlyTheMarkedLines(String fixtureName) throws Exception {
        Path fixture = fixture(fixtureName);

        List<Finding> findings =
                CheckstyleRunner.run(VariableNamedAfterTypeCheck.class, Map.of("types", TYPES), fixture);

        assertThat(findings).extracting(Finding::line).containsExactlyElementsOf(CheckstyleRunner.markedLines(fixture));
    }

    @Test
    void acceptsAQualifierWhenAllowed() throws Exception {
        Path fixture = fixture(QUALIFIER);

        List<Finding> findings = CheckstyleRunner.run(
                VariableNamedAfterTypeCheck.class, Map.of("types", TYPES + ", URL", "allowQualifier", "true"), fixture);

        assertThat(findings).extracting(Finding::line).containsExactlyElementsOf(CheckstyleRunner.markedLines(fixture));
    }

    @Test
    void rejectsAQualifierByDefault() throws Exception {
        List<Finding> findings =
                CheckstyleRunner.run(VariableNamedAfterTypeCheck.class, Map.of("types", TYPES), fixture(QUALIFIER));

        assertThat(findings).extracting(Finding::line).containsExactly(6, 8, 9, 10, 11, 12, 14, 15);
    }

    @Test
    void onlyTheConfiguredPatternsSelectATypeAndAnAcronymKeepsItsLastCapital() throws Exception {
        List<Finding> findings = CheckstyleRunner.run(
                VariableNamedAfterTypeCheck.class,
                Map.of("types", "AIService, URL", "allowQualifier", "true"),
                fixture(QUALIFIER));

        assertThat(findings)
                .extracting(Finding::message)
                .containsExactly(
                        "'aIService' of type 'AIService' is not named 'aiService' (or '<qualifier>AIService').",
                        "'service' of type 'AIService' is not named 'aiService' (or '<qualifier>AIService').",
                        "'link' of type 'URL' is not named 'url' (or '<qualifier>URL').");
    }

    @Test
    void messageNamesTheIdTheVariableTheTypeTheExpectedNameAndTheSuggestion() throws Exception {
        Map<String, String> properties = Map.of(
                "id", "TAP-X1", "types", TYPES, "allowQualifier", "true", "suggestion", "Name it after its type.");

        List<Finding> findings =
                CheckstyleRunner.run(VariableNamedAfterTypeCheck.class, properties, fixture(DECLARATIONS));

        assertThat(findings.getFirst())
                .isEqualTo(new Finding(
                        9,
                        findings.getFirst().column(),
                        "TAP-X1",
                        "TAP-X1: 'users' of type 'UserRepositoryPort' is not named 'userRepositoryPort'"
                                + " (or '<qualifier>UserRepositoryPort'). Name it after its type."));
    }

    @Test
    void messageWithoutIdQualifierAndSuggestionIsTheSentence() throws Exception {
        List<Finding> findings =
                CheckstyleRunner.run(VariableNamedAfterTypeCheck.class, Map.of("types", TYPES), fixture(DECLARATIONS));

        assertThat(findings.getFirst().id()).isNull();
        assertThat(findings.getFirst().message())
                .isEqualTo("'users' of type 'UserRepositoryPort' is not named 'userRepositoryPort'.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Foo..Bar", "1Service", "Service.", "*Service*Port"})
    void invalidPatternIsRejectedWithItsText(String pattern) {
        VariableNamedAfterTypeCheck check = new VariableNamedAfterTypeCheck();

        assertThatThrownBy(() -> check.setTypes("*Service", pattern))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'" + pattern + "'");
    }

    @Test
    void invalidValueFailsTheConfiguration() {
        assertThatThrownBy(() -> CheckstyleRunner.run(
                        VariableNamedAfterTypeCheck.class, Map.of("types", "Foo..Bar"), fixture(DECLARATIONS)))
                .isInstanceOf(CheckstyleException.class)
                .hasStackTraceContaining("'Foo..Bar'");
    }

    @Test
    void typesAreRequired() {
        VariableNamedAfterTypeCheck check = new VariableNamedAfterTypeCheck();

        assertThatThrownBy(check::finishLocalSetup)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("VariableNamedAfterTypeCheck: 'types' is required");
    }

    @Test
    void missingTypesFailTheConfiguration() {
        assertThatThrownBy(
                        () -> CheckstyleRunner.run(VariableNamedAfterTypeCheck.class, Map.of(), fixture(DECLARATIONS)))
                .hasStackTraceContaining("'types' is required");
    }

    @Test
    void registersTheTokensItNeeds() {
        VariableNamedAfterTypeCheck check = new VariableNamedAfterTypeCheck();
        int[] expected = {
            TokenTypes.VARIABLE_DEF, TokenTypes.PARAMETER_DEF, TokenTypes.RECORD_COMPONENT_DEF, TokenTypes.RESOURCE
        };

        assertThat(check.getDefaultTokens()).containsExactly(expected);
        assertThat(check.getAcceptableTokens()).containsExactly(expected);
        assertThat(check.getRequiredTokens()).containsExactly(expected);
    }

    private static Path fixture(String name) {
        try {
            return Path.of(VariableNamedAfterTypeCheckTest.class
                    .getResource("/checks/VariableNamedAfterTypeCheck/" + name)
                    .toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
