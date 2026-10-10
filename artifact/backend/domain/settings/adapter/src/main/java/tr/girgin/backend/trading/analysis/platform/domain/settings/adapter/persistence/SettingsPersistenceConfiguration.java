package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The settings domain's database schema and its migrations. The one place of the domain that names the
 * schema; the entities and the Flyway bean use {@link #SCHEMA}. See docs/coding-convention/backend-java-persistence.md.
 */
@Configuration
public class SettingsPersistenceConfiguration {

    public static final String SCHEMA = "SETTINGS";

    /** Migrates when the bean is created, so Hibernate (which depends on every Flyway bean) validates afterwards. */
    @Bean(initMethod = "migrate")
    public Flyway settingsFlyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .createSchemas(true)
                .table("FLYWAY_SCHEMA_HISTORY")
                .locations("classpath:"
                        + SettingsPersistenceConfiguration.class
                                .getPackageName()
                                .replace('.', '/') + "/migration")
                .load();
    }
}
