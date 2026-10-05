package tr.girgin.checkstyle;

import static org.assertj.core.api.Assertions.assertThat;

import com.puppycrawl.tools.checkstyle.api.Configuration;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The documentation of the custom checks is complete: every check has a reference document that the index
 * links and whose parameter table lists exactly the check's parameters, and every project rule configured
 * in {@code config/checkstyle/checkstyle.xml} has a row in the rules catalogue.
 */
class DocumentationTest {

    private static final String CHECK_PACKAGE = "tr.girgin.checkstyle";
    private static final Pattern PARAMETER_ROW = Pattern.compile("^\\|\\s*`([A-Za-z]+)`\\s*\\|");

    @Test
    void thereIsAtLeastOneCheck() throws IOException {
        assertThat(checkNames()).isNotEmpty();
    }

    @ParameterizedTest
    @MethodSource("checkNames")
    void everyCheckHasAReferenceDocument(String check) {
        assertThat(referenceDocument(check))
                .as("reference document of %s", check)
                .isRegularFile();
    }

    @ParameterizedTest
    @MethodSource("checkNames")
    void theIndexLinksEveryCheck(String check) throws IOException {
        String index = Files.readString(docs().resolve("backend-java-checkstyle-custom-checks.md"));

        assertThat(index).contains("(backend-java-checkstyle-checks/" + check + ".md)");
    }

    @ParameterizedTest
    @MethodSource("checkNames")
    void theParameterTableListsExactlyTheParametersOfTheCheck(String check) throws Exception {
        List<String> documented = documentedParameters(Files.readAllLines(referenceDocument(check)));

        assertThat(documented)
                .as("parameters in the table of %s", check)
                .containsExactlyInAnyOrderElementsOf(parameters(check));
    }

    @Test
    void theRulesCatalogueHasARowForEveryProjectRule() throws Exception {
        String catalogue = Files.readString(docs().resolve("backend-java-checkstyle.md"));
        Configuration config = ProjectConfig.load();

        for (Configuration rule : ProjectConfig.projectRules(config)) {
            String id = ProjectConfig.id(rule);
            assertThat(catalogue).as("rules catalogue row of %s", id).contains("| `" + id + "` |");
        }
    }

    static List<String> checkNames() throws IOException {
        Path sources = ProjectConfig.repositoryRoot()
                .resolve("build-logic/checkstyle-rules/src/main/java/tr/girgin/checkstyle");
        try (Stream<Path> files = Files.list(sources)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith("Check.java"))
                    .map(name -> name.substring(0, name.length() - ".java".length()))
                    .sorted()
                    .toList();
        }
    }

    /** The public setters of the check class itself, as parameter names; {@code id} and the like are inherited. */
    private static List<String> parameters(String check) throws ClassNotFoundException {
        Class<?> type = Class.forName(CHECK_PACKAGE + "." + check);
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .filter(name -> name.startsWith("set"))
                .map(name -> Character.toLowerCase(name.charAt(3)) + name.substring(4))
                .toList();
    }

    /** The first column of the table under the {@code ## Parameters} heading. */
    private static List<String> documentedParameters(List<String> lines) {
        int start = lines.indexOf("## Parameters");
        assertThat(start).as("a '## Parameters' section").isNotNegative();
        return lines.stream()
                .skip(start + 1L)
                .takeWhile(line -> !line.startsWith("## "))
                .map(PARAMETER_ROW::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group(1))
                .toList();
    }

    private static Path referenceDocument(String check) {
        return docs().resolve("backend-java-checkstyle-checks/" + check + ".md");
    }

    private static Path docs() {
        return ProjectConfig.repositoryRoot().resolve("docs/coding-convention");
    }
}
