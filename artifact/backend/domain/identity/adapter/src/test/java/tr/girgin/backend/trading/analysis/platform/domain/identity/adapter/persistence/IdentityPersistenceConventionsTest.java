package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence;

import tr.girgin.backend.trading.analysis.platform.library.persistence.DomainPersistenceConventionsTest;

/** The identity domain's schema rules: entities, migrations, history table and names. */
class IdentityPersistenceConventionsTest extends DomainPersistenceConventionsTest {

    IdentityPersistenceConventionsTest() {
        super(
                IdentityPersistenceConfiguration.class,
                IdentityPersistenceConfiguration.SCHEMA,
                dataSource -> new IdentityPersistenceConfiguration().identityFlyway(dataSource));
    }
}
