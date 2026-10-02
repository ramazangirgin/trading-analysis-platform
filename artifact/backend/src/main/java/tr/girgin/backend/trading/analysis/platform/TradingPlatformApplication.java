package tr.girgin.backend.trading.analysis.platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

/**
 * Fully qualified bean names: every module has MapStruct mappers, and generated beans with the
 * same simple name in different packages (e.g. two DurationToMillisMapper) would otherwise clash.
 */
@SpringBootApplication(nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@SuppressWarnings("checkstyle:HideUtilityClassConstructor") // Spring instantiates the @SpringBootApplication class
public class TradingPlatformApplication {

    public static void main(String[] args) throws IOException {
        // SQLite does not create the database's directory itself.
        Files.createDirectories(PlatformHome.resolve());
        SpringApplication.run(TradingPlatformApplication.class, args);
    }

    /** Mirrors platform.home in application.properties, which is not resolvable before startup. */
    static final class PlatformHome {

        private PlatformHome() {
        }

        static Path resolve() {
            String configured = System.getProperty("platform.home", System.getenv("PLATFORM_HOME"));
            return configured != null && !configured.isBlank()
                    ? Path.of(configured)
                    : Path.of(System.getProperty("user.home"), ".tradingagents-platform");
        }
    }
}
