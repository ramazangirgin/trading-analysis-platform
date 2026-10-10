package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence;

import tr.girgin.backend.trading.analysis.platform.library.persistence.DomainPersistenceConventionsTest;

/** The settings domain's schema rules: entities, migrations, history table and names. */
class SettingsPersistenceConventionsTest extends DomainPersistenceConventionsTest {

    SettingsPersistenceConventionsTest() {
        super(
                SettingsPersistenceConfiguration.class,
                SettingsPersistenceConfiguration.SCHEMA,
                dataSource -> new SettingsPersistenceConfiguration().settingsFlyway(dataSource));
    }
}
