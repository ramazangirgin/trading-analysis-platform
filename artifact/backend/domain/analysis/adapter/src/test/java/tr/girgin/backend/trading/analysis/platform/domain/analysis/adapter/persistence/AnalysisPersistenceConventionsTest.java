package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence;

import tr.girgin.backend.trading.analysis.platform.library.persistence.DomainPersistenceConventionsTest;

/** The analysis domain's schema rules: entities, migrations, history table and names. */
class AnalysisPersistenceConventionsTest extends DomainPersistenceConventionsTest {

    AnalysisPersistenceConventionsTest() {
        super(
                AnalysisPersistenceConfiguration.class,
                AnalysisPersistenceConfiguration.SCHEMA,
                dataSource -> new AnalysisPersistenceConfiguration().analysisFlyway(dataSource));
    }
}
