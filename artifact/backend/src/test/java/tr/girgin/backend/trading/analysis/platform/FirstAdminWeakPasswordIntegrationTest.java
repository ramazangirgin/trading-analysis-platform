package tr.girgin.backend.trading.analysis.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.NestedExceptionUtils;
import tr.girgin.backend.trading.analysis.platform.library.persistence.TestDatabaseConfiguration;

/** The password policy at startup: a weak configured first-admin password or a bad minimum stops it. */
class FirstAdminWeakPasswordIntegrationTest {

    private static final Path HOME = TestPlatformHome.create();
    private static final String WEAK_PASSWORD = "Xy7-weak";

    /** Starts the application; the settings are command-line arguments, so they win over application.properties. */
    private static void start(String... settings) {
        String[] args = Stream.concat(
                        Stream.of(
                                "server.port=0",
                                "platform.home=" + HOME,
                                "platform.data-dir=" + HOME,
                                "platform.runner.process.command=/bin/sh," + HOME.resolve("runner.sh"),
                                "platform.runner.process.working-dir=" + HOME,
                                "platform.secrets.env-file=" + HOME.resolve("secrets.env"),
                                "platform.secrets.external-env-files="),
                        Stream.of(settings))
                .map(setting -> "--" + setting)
                .toArray(String[]::new);
        new SpringApplicationBuilder(TradingPlatformApplication.class, TestDatabaseConfiguration.class)
                .run(args)
                .close();
    }

    @Test
    void aWeakConfiguredAdminPasswordStopsTheStartupAndIsNotRevealed() {
        assertThatThrownBy(() -> start("platform.admin.password=" + WEAK_PASSWORD))
                .satisfies(failure -> {
                    Throwable root = NestedExceptionUtils.getMostSpecificCause(failure);
                    assertThat(root).isInstanceOf(IllegalStateException.class);
                    assertThat(root.getMessage())
                            .contains("PLATFORM_ADMIN_PASSWORD")
                            .contains("too_short")
                            .doesNotContain(WEAK_PASSWORD);
                });
    }

    @Test
    void aMinimumLengthOutsideItsRangeStopsTheStartup() {
        assertThatThrownBy(() -> start("platform.password.min-length=7")).satisfies(failure -> {
            Throwable root = NestedExceptionUtils.getMostSpecificCause(failure);
            assertThat(root).isInstanceOf(IllegalArgumentException.class);
            assertThat(root.getMessage()).contains("platform.password.min-length");
        });
    }
}
