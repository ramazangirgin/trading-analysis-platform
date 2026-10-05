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

class ForbiddenMemberAccessCheckTest {

    private static final String SYSTEM_OUT_FIXTURE = "SystemStreams.java";

    @ParameterizedTest(name = "{0} on {1}")
    @CsvSource(delimiter = '|', textBlock = """
                    System#out, System#err                       | SystemStreams.java
                    *#*                                          | Headers.java
                    Instant#now(0)                               | InstantNow.java
                    java.time.Instant#now(0)                     | InstantNow.java
                    Instant#now(0)                               | SimpleNames.java
                    java.lang.System#out                         | QualifiedJavaLang.java
                    java.lang.System#out                         | QualifiedShadowed.java
                    java.lang.System#out                         | QualifiedExplicitImport.java
                    java.time.Instant#now(0)                     | QualifiedOnDemand.java
                    java.time.Instant#now(0)                     | QualifiedSamePackage.java
                    java.time.Instant#now(0)                     | QualifiedNoImport.java
                    Thread#sleep(), System#out, Random#new()     | AnyArguments.java
                    System#out, Random#new(), *#printStackTrace(0) | AnyReceiver.java
                    Executors#new*()                             | NamePrefix.java
                    TimeUnit.*#sleep()                           | MemberOfType.java
                    Random#new(), System#out, Thread#sleep()     | Constructors.java
                    Random#new(1)                                | ConstructorArguments.java
                    *#new(0)                                     | AnyConstructor.java
                    *#INSTANCE                                   | AnyReceiverField.java
                    System#out, *#out                            | MultiplePatterns.java
                    """)
    void reportsExactlyTheMarkedLines(String members, String fixtureName) throws Exception {
        Path fixture = fixture(fixtureName);

        List<Finding> findings =
                CheckstyleRunner.run(ForbiddenMemberAccessCheck.class, Map.of("members", members), fixture);

        assertThat(findings).extracting(Finding::line).containsExactlyElementsOf(CheckstyleRunner.markedLines(fixture));
    }

    @Test
    void messageNamesTheIdTheMemberAndTheSuggestion() throws Exception {
        Map<String, String> properties =
                Map.of("id", "TAP-X1", "members", "System#out", "suggestion", "Use an SLF4J logger: log.info(...).");

        List<Finding> findings =
                CheckstyleRunner.run(ForbiddenMemberAccessCheck.class, properties, fixture(SYSTEM_OUT_FIXTURE));

        assertThat(findings).hasSize(4);
        assertThat(findings.getFirst())
                .isEqualTo(new Finding(
                        5,
                        findings.getFirst().column(),
                        "TAP-X1",
                        "TAP-X1: Field 'System#out' is not allowed. Use an SLF4J logger: log.info(...)."));
    }

    @Test
    void messageWithoutSuggestionEndsAfterTheSentence() throws Exception {
        Map<String, String> properties = Map.of("id", "TAP-X1", "members", "System#out");

        List<Finding> findings =
                CheckstyleRunner.run(ForbiddenMemberAccessCheck.class, properties, fixture(SYSTEM_OUT_FIXTURE));

        assertThat(findings.getFirst().message()).isEqualTo("TAP-X1: Field 'System#out' is not allowed.");
    }

    @Test
    void messageWithoutIdStartsWithTheSentence() throws Exception {
        Map<String, String> properties = Map.of("members", "System#out", "suggestion", "Log instead.");

        List<Finding> findings =
                CheckstyleRunner.run(ForbiddenMemberAccessCheck.class, properties, fixture(SYSTEM_OUT_FIXTURE));

        assertThat(findings.getFirst().id()).isNull();
        assertThat(findings.getFirst().message()).isEqualTo("Field 'System#out' is not allowed. Log instead.");
    }

    @Test
    void methodAndConstructorMessagesNameTheCallKind() throws Exception {
        Map<String, String> properties =
                Map.of("id", "TAP-X1", "members", "Thread#sleep(), Random#new()", "suggestion", "Inject it.");

        assertThat(CheckstyleRunner.run(ForbiddenMemberAccessCheck.class, properties, fixture("AnyArguments.java")))
                .extracting(Finding::message)
                .first()
                .isEqualTo("TAP-X1: Call of 'Thread#sleep()' is not allowed. Inject it.");
        assertThat(CheckstyleRunner.run(ForbiddenMemberAccessCheck.class, properties, fixture("Constructors.java")))
                .extracting(Finding::message)
                .first()
                .isEqualTo("TAP-X1: Call of constructor 'Random#new()' is not allowed. Inject it.");
    }

    @Test
    void reportsEveryFindingOnceEvenWhenSeveralPatternsMatch() throws Exception {
        List<Finding> findings = CheckstyleRunner.run(
                ForbiddenMemberAccessCheck.class,
                Map.of("members", "System#out, *#out"),
                fixture("MultiplePatterns.java"));

        assertThat(findings).extracting(Finding::message).first().asString().contains("'System#out'");
        assertThat(findings).hasSize(2);
    }

    @Test
    void stateDoesNotLeakFromOneFileToTheNext() throws Exception {
        var configuration = CheckstyleRunner.checkerWith(
                ForbiddenMemberAccessCheck.class, Map.of("members", "java.time.Instant#now(0)"));

        List<Finding> findings = CheckstyleRunner.run(
                configuration, fixture("QualifiedOnDemand.java"), fixture("QualifiedNoImport.java"));

        // QualifiedOnDemand imports java.time.*; QualifiedNoImport does not, and must not inherit that.
        assertThat(findings).extracting(Finding::line).containsExactly(7, 8);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "System",
                "System#",
                "#out",
                "System#out(",
                "System#out(x)",
                "System#out(-1)",
                "System#out(1234567)",
                "System#out()x",
                "1System#out",
                "System#ou-t",
                "Sys tem#out",
                "System#out*x",
                "System.*#new()",
                ".*#out",
                "a..b#out",
                "*.*#out",
            })
    void invalidPatternIsRejectedWithItsText(String pattern) {
        ForbiddenMemberAccessCheck check = new ForbiddenMemberAccessCheck();

        assertThatThrownBy(() -> check.setMembers("System#err", pattern))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid member pattern '" + pattern + "': ");
    }

    @Test
    void invalidPatternFailsTheConfiguration() {
        assertThatThrownBy(() -> CheckstyleRunner.run(
                        ForbiddenMemberAccessCheck.class, Map.of("members", "System"), fixture(SYSTEM_OUT_FIXTURE)))
                .isInstanceOf(CheckstyleException.class)
                .hasStackTraceContaining("Invalid member pattern 'System': expected Type#member");
    }

    @Test
    void membersAreRequired() {
        ForbiddenMemberAccessCheck check = new ForbiddenMemberAccessCheck();
        check.setMembers();

        assertThatThrownBy(check::finishLocalSetup)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ForbiddenMemberAccessCheck: 'members' is required");
    }

    @Test
    void missingMembersFailTheConfiguration() {
        assertThatThrownBy(() -> CheckstyleRunner.run(
                        ForbiddenMemberAccessCheck.class, Map.of("suggestion", "x"), fixture(SYSTEM_OUT_FIXTURE)))
                .hasStackTraceContaining("'members' is required");
    }

    @Test
    void registersTheTokensItNeeds() {
        ForbiddenMemberAccessCheck check = new ForbiddenMemberAccessCheck();
        int[] expected = {TokenTypes.DOT, TokenTypes.METHOD_CALL, TokenTypes.LITERAL_NEW};

        assertThat(check.getDefaultTokens()).containsExactly(expected);
        assertThat(check.getAcceptableTokens()).containsExactly(expected);
        assertThat(check.getRequiredTokens()).containsExactly(expected);
    }

    private static Path fixture(String name) {
        try {
            return Path.of(ForbiddenMemberAccessCheckTest.class
                    .getResource("/checks/ForbiddenMemberAccessCheck/" + name)
                    .toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
