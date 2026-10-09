package tr.girgin.backend.trading.analysis.platform.library.persistence.fixture.sample;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The persistence configuration of the made-up domain "sample", written as a real domain writes its own. */
@Configuration
public class SamplePersistenceConfiguration {

    public static final String SCHEMA = "SAMPLE";

    @Bean(initMethod = "migrate")
    public Flyway sampleFlyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .createSchemas(true)
                .table("FLYWAY_SCHEMA_HISTORY")
                .locations("classpath:"
                        + SamplePersistenceConfiguration.class.getPackageName().replace('.', '/') + "/migration")
                .load();
    }
}
