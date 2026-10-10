package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AssetType;
import tr.girgin.backend.trading.analysis.platform.library.mapper.DurationToMillisMapper;
import tr.girgin.backend.trading.analysis.platform.library.persistence.JpaAdapterTest;

/**
 * All analysis adapters wired as in the application, against a temporary platform home and a
 * PostgreSQL database that the domain's own Flyway bean migrates. The runner command is a shell script
 * each test writes itself.
 */
@JpaAdapterTest
@SpringJUnitConfig(AdapterTestSupport.Config.class)
public abstract class AdapterTestSupport {

    protected static final Path HOME = createTempDirectory();
    protected static final Path RUNNER_SCRIPT = HOME.resolve("fake-runner.sh");
    protected static final Path ENV_FILE = HOME.resolve("secrets.env");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("platform.home", HOME::toString);
        registry.add("platform.runner.process.command", () -> "/bin/sh," + RUNNER_SCRIPT);
        registry.add("platform.runner.process.working-dir", HOME::toString);
        registry.add("platform.runner.stop-grace-seconds", () -> "2");
        registry.add("platform.secrets.env-file", ENV_FILE::toString);
        registry.add("platform.secrets.external-env-files", () -> "");
    }

    protected static AnalysisSpec spec(String ticker) {
        return new AnalysisSpec(
                ticker,
                LocalDate.of(2026, 9, 25),
                AssetType.STOCK,
                List.of(Analyst.NEWS, Analyst.MARKET),
                "deepseek",
                "deepseek-v4-pro",
                "deepseek-v4-flash",
                1,
                2,
                "Turkish",
                true);
    }

    private static Path createTempDirectory() {
        try {
            return Files.createTempDirectory("analysis-adapter-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Configuration
    @AutoConfigurationPackage
    @ComponentScan(basePackageClasses = {AdapterTestSupport.class, DurationToMillisMapper.class})
    static class Config {}
}
