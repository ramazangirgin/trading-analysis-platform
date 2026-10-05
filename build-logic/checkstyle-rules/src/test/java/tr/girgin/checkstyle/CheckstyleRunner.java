package tr.girgin.checkstyle;

import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.DefaultConfiguration;
import com.puppycrawl.tools.checkstyle.TreeWalker;
import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import com.puppycrawl.tools.checkstyle.api.AuditListener;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.Configuration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Runs Checkstyle on fixture files and returns the findings. Checkstyle's own test support is not published
 * as a library, hence this small harness.
 *
 * <p>Fixtures mark every line that must be reported with a trailing {@code // violation} comment; a test
 * asserts that the reported lines are exactly the marked ones, so a missing and an extra finding both fail.
 */
final class CheckstyleRunner {

    private static final String VIOLATION_MARKER = "// violation";

    /** One reported finding. */
    record Finding(int line, int column, String id, String message) {}

    private CheckstyleRunner() {}

    /** A check, configured with the given properties, inside a {@code TreeWalker} inside a {@code Checker}. */
    static Configuration checkerWith(Class<?> check, Map<String, String> properties) {
        DefaultConfiguration module = new DefaultConfiguration(check.getName());
        properties.forEach(module::addProperty);
        return checkerWith(List.of(), List.of(module));
    }

    /**
     * A {@code Checker} with the given modules directly below it (filters) and the given modules below its
     * {@code TreeWalker}, which also gets {@code SuppressWarningsHolder}.
     */
    static Configuration checkerWith(List<Configuration> checkerModules, List<Configuration> treeWalkerModules) {
        DefaultConfiguration treeWalker = new DefaultConfiguration(TreeWalker.class.getName());
        treeWalker.addChild(new DefaultConfiguration("SuppressWarningsHolder"));
        treeWalkerModules.forEach(treeWalker::addChild);
        DefaultConfiguration checker = new DefaultConfiguration(Checker.class.getName());
        checker.addProperty("severity", "error");
        checkerModules.forEach(checker::addChild);
        checker.addChild(treeWalker);
        return checker;
    }

    /** Runs the configuration on the files and returns what it reports, ordered by file, line and column. */
    static List<Finding> run(Configuration configuration, Path... files) throws CheckstyleException {
        List<Finding> findings = new ArrayList<>();
        Checker checker = new Checker();
        try {
            checker.setModuleClassLoader(CheckstyleRunner.class.getClassLoader());
            checker.configure(configuration);
            checker.addListener(collecting(findings));
            checker.process(Arrays.stream(files).map(Path::toFile).toList());
        } finally {
            checker.destroy();
        }
        return findings;
    }

    /** Runs one check with the given properties on one fixture. */
    static List<Finding> run(Class<?> check, Map<String, String> properties, Path file) throws CheckstyleException {
        return run(checkerWith(check, properties), file);
    }

    /** The line numbers (1-based) of the lines marked {@code // violation}, in order. */
    static List<Integer> markedLines(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        return IntStream.range(0, lines.size())
                .filter(index -> lines.get(index).contains(VIOLATION_MARKER))
                .mapToObj(index -> index + 1)
                .toList();
    }

    private static AuditListener collecting(List<Finding> findings) {
        return new AuditListener() {
            @Override
            public void auditStarted(AuditEvent event) {}

            @Override
            public void auditFinished(AuditEvent event) {}

            @Override
            public void fileStarted(AuditEvent event) {}

            @Override
            public void fileFinished(AuditEvent event) {}

            @Override
            public void addError(AuditEvent event) {
                findings.add(new Finding(event.getLine(), event.getColumn(), event.getModuleId(), event.getMessage()));
            }

            @Override
            public void addException(AuditEvent event, Throwable throwable) {
                throw new IllegalStateException("Checkstyle failed on " + event.getFileName(), throwable);
            }
        };
    }
}
