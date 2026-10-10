package tr.girgin.backend.trading.analysis.platform;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;

/**
 * The test fixtures of a library ({@code @JpaAdapterTest}, {@code TestDatabaseConfiguration} and the like) are
 * test code, not production code.
 */
final class DoNotIncludeTestFixtures implements ImportOption {

    @Override
    public boolean includes(Location location) {
        return !location.contains("-test-fixtures.jar") && !location.contains("/testFixtures/");
    }
}
