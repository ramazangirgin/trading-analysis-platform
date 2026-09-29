package tr.girgin.backend.trading.analysis.platform;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.test.context.DynamicPropertyRegistry;

/** A throwaway platform home per test class, so tests never touch ~/.tradingagents-platform. */
final class TestPlatformHome {

    private TestPlatformHome() {
    }

    static Path create() {
        try {
            return Files.createTempDirectory("platform-home");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void register(DynamicPropertyRegistry registry, Path home, Path runnerScript) {
        registry.add("platform.home", home::toString);
        registry.add("platform.runner.process.command", () -> "/bin/sh," + runnerScript);
        registry.add("platform.runner.process.working-dir", home::toString);
        registry.add("platform.secrets.env-file", () -> home.resolve("secrets.env").toString());
        registry.add("platform.secrets.external-env-files", () -> "");
        registry.add("platform.results-dir", () -> home.resolve("logs").toString());
        registry.add("platform.cache-dir", () -> home.resolve("cache").toString());
    }
}
