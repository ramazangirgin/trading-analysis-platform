package tr.girgin.backend.trading.analysis.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The stored names of the application's schema after every migration on its classpath follow
 * docs/coding-convention/backend-database-naming.md. {@link DatabaseNamingCheckTest} proves the check.
 */
@SpringBootTest
class DatabaseNamingTest {

    private static final Path HOME = TestPlatformHome.create();

    @Autowired
    private DataSource dataSource;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestPlatformHome.register(registry, HOME, HOME.resolve("runner.sh"));
    }

    @Test
    void everyStoredNameFollowsTheConvention() {
        DatabaseNamingCheck.Result result = DatabaseNamingCheck.run(dataSource);

        assertTrue(
                result.tables()
                        .containsAll(List.of(
                                "ANALYSES", "PRESETS", "USERS", "ROLES", "USER_ROLES", "FLYWAY_SCHEMA_HISTORY")),
                "the check did not read the schema, it saw " + result.tables());
        assertEquals(List.of(), result.violations(), "database names that break backend-database-naming.md");
    }
}
