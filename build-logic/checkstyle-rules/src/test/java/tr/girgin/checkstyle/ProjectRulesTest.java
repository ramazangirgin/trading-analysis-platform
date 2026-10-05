package tr.girgin.checkstyle;

import static org.assertj.core.api.Assertions.assertThat;

import com.puppycrawl.tools.checkstyle.api.Configuration;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tr.girgin.checkstyle.CheckstyleRunner.Finding;

/**
 * Proves every project rule configured in {@code config/checkstyle/checkstyle.xml} on fixtures, so a rule
 * cannot be added without them.
 *
 * <p>For a rule {@code TAP-X1}, {@code rules/TAP-X1/} holds the fixtures. In a file named
 * {@code Violation*.java} every line that must be reported is marked {@code // violation}; the rule must
 * report exactly those lines, with its id. Every other file ({@code Compliant.java}, a suppression
 * fixture, ...) must report nothing. The optional file {@code sources} says where the rule applies:
 * {@code main}, {@code test} or {@code both} (the default). Each fixture is copied to {@code src/main/java}
 * and / or {@code src/test/java} first, because the configuration scopes rules by path.
 */
class ProjectRulesTest {

    private static final String VIOLATION_FILE_PREFIX = "Violation";

    @Test
    void theConfigurationHasProjectRules() throws Exception {
        assertThat(ProjectConfig.projectRules(ProjectConfig.load())).isNotEmpty();
    }

    @TestFactory
    Stream<DynamicTest> everyProjectRuleReportsExactlyItsViolations() throws Exception {
        Configuration config = ProjectConfig.load();
        List<Configuration> filters = ProjectConfig.filters(config);
        return ProjectConfig.projectRules(config).stream().flatMap(rule -> {
            String id = ProjectConfig.id(rule);
            return sourceSets(id).stream()
                    .map(sourceSet -> DynamicTest.dynamicTest(
                            id + " in src/" + sourceSet, () -> proveRule(rule, id, sourceSet, filters)));
        });
    }

    private static void proveRule(Configuration rule, String id, String sourceSet, List<Configuration> filters)
            throws Exception {
        Path fixtures = fixtureDirectory(id);
        List<Path> files = javaFiles(fixtures);
        assertThat(files)
                .as("%s needs a Violation.java and a Compliant.java in rules/%s", id, id)
                .extracting(path -> path.getFileName().toString())
                .contains("Violation.java", "Compliant.java");

        Path sources = Files.createTempDirectory("rule-fixtures").resolve("src/" + sourceSet + "/java");
        Files.createDirectories(sources);
        try {
            Configuration checker = CheckstyleRunner.checkerWith(filters, List.of(rule));
            for (Path fixture : files) {
                Path copy = Files.copy(
                        fixture, sources.resolve(fixture.getFileName().toString()));
                List<Finding> findings = CheckstyleRunner.run(checker, copy);
                List<Integer> expected = fixture.getFileName().toString().startsWith(VIOLATION_FILE_PREFIX)
                        ? CheckstyleRunner.markedLines(fixture)
                        : List.of();
                assertThat(findings)
                        .as("%s on %s in src/%s", id, fixture.getFileName(), sourceSet)
                        .extracting(Finding::line)
                        .containsExactlyElementsOf(expected);
                assertThat(findings).allMatch(finding -> id.equals(finding.id()));
                if (fixture.getFileName().toString().equals("Violation.java")) {
                    assertThat(expected)
                            .as("Violation.java marks lines with // violation")
                            .isNotEmpty();
                }
            }
        } finally {
            deleteRecursively(sources.getParent().getParent().getParent());
        }
    }

    private static List<String> sourceSets(String id) {
        try {
            Path file = fixtureDirectory(id).resolve("sources");
            String value = Files.exists(file) ? Files.readString(file).trim() : "both";
            return switch (value) {
                case "main" -> List.of("main");
                case "test" -> List.of("test");
                default -> List.of("main", "test");
            };
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path fixtureDirectory(String id) {
        URL url = ProjectRulesTest.class.getResource("/rules/" + id);
        assertThat(url)
                .as("rule %s is configured in checkstyle.xml but has no fixtures in rules/%s", id, id)
                .isNotNull();
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<Path> javaFiles(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private static void deleteRecursively(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
