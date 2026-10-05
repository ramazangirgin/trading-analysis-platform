package tr.girgin.checkstyle;

import com.puppycrawl.tools.checkstyle.ConfigurationLoader;
import com.puppycrawl.tools.checkstyle.PropertiesExpander;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.Configuration;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/** The project's real Checkstyle configuration, {@code config/checkstyle/checkstyle.xml}, and its project rules. */
final class ProjectConfig {

    /** Id prefix of the project's own rules. */
    static final String RULE_ID_PREFIX = "TAP-";

    private ProjectConfig() {}

    /** The repository root; the build passes it as a system property. */
    static Path repositoryRoot() {
        return Path.of(System.getProperty("repository.root"));
    }

    static Configuration load() throws CheckstyleException {
        Path file = repositoryRoot().resolve("config/checkstyle/checkstyle.xml");
        return ConfigurationLoader.loadConfiguration(file.toString(), new PropertiesExpander(new Properties()));
    }

    /** The modules directly below the {@code Checker} that filter findings (suppressions). */
    static List<Configuration> filters(Configuration checker) {
        return Arrays.stream(checker.getChildren())
                .filter(child -> child.getName().endsWith("Filter"))
                .toList();
    }

    /** The configured instances of the custom checks: the modules below {@code TreeWalker} with a {@code TAP-} id. */
    static List<Configuration> projectRules(Configuration checker) {
        Configuration treeWalker = Arrays.stream(checker.getChildren())
                .filter(child -> child.getName().equals("TreeWalker"))
                .findFirst()
                .orElseThrow();
        return Arrays.stream(treeWalker.getChildren())
                .filter(module -> id(module) != null && id(module).startsWith(RULE_ID_PREFIX))
                .toList();
    }

    /** The module's {@code id}, or {@code null} when it has none. */
    static String id(Configuration module) {
        try {
            return module.getProperty("id");
        } catch (CheckstyleException _) {
            return null;
        }
    }
}
