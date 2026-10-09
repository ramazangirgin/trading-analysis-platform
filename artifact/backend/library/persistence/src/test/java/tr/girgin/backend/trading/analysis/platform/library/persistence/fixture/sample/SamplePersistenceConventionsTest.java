package tr.girgin.backend.trading.analysis.platform.library.persistence.fixture.sample;

import com.tngtech.archunit.core.importer.ImportOption;
import tr.girgin.backend.trading.analysis.platform.library.persistence.DomainPersistenceConventionsTest;

/**
 * Proves {@link DomainPersistenceConventionsTest} works end to end: the correct made-up domain "sample"
 * passes all four tests. In another package than the abstract class, as a real domain's test is. The
 * sample's classes are test classes, so this one counts them.
 */
class SamplePersistenceConventionsTest extends DomainPersistenceConventionsTest {

    SamplePersistenceConventionsTest() {
        super(
                SamplePersistenceConfiguration.class,
                SamplePersistenceConfiguration.SCHEMA,
                dataSource -> new SamplePersistenceConfiguration().sampleFlyway(dataSource));
    }

    @Override
    protected ImportOption importOption() {
        return _ -> true;
    }
}
