package tr.girgin.backend.trading.analysis.platform.library.persistence;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnectionAutoConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * The JPA wiring of a persistence adapter test: Flyway's auto-configuration (it backs off, as in the application,
 * for the domain's own Flyway bean, but makes Hibernate wait for it), Hibernate, Spring Data JPA repositories and
 * transactions as in the application, with the JPA settings of {@code jpa-test.properties}
 * ({@code ddl-auto=validate}, the naming strategy, global quoting), JPA auditing, and a
 * {@link MutableTestClock} as the {@code Clock} bean that a test injects to control the time. The test's own
 * configuration provides no data source: {@link TestDatabaseConfiguration} contributes a PostgreSQL container per
 * context as a {@code @ServiceConnection}, {@code ServiceConnectionAutoConfiguration} turns it into the connection
 * details and {@code DataSourceAutoConfiguration} builds the pool from them. A plain {@code @SpringJUnitConfig}
 * context does not apply the initializers of {@code spring.factories} the way {@code SpringApplication} does, so
 * {@link TestcontainersLifecycleContextCustomizerFactory} (registered in {@code spring.factories} of the fixtures,
 * for classes with this annotation) applies Boot's {@code TestcontainersLifecycleApplicationContextInitializer}: it
 * starts the container before the pool connects and stops it when the context closes. (A
 * {@code @ContextConfiguration(initializers)} on this annotation would clash with the test's own
 * {@code @SpringJUnitConfig} and drop its classes.) The test's configuration
 * carries {@code @AutoConfigurationPackage}, so entity and repository scan stay in its module. Its component scan
 * picks up the domain's persistence configuration, whose Flyway bean migrates the database before Hibernate
 * validates.
 *
 * <p>No test-managed transaction: every call of a port runs in its own transaction, as in production.
 * This is not {@code @DataJpaTest}, whose per-test transaction would hide what the database sees.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ImportAutoConfiguration({
    DataSourceAutoConfiguration.class,
    ServiceConnectionAutoConfiguration.class,
    FlywayAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    DataJpaRepositoriesAutoConfiguration.class,
    TransactionAutoConfiguration.class
})
@Import({JpaAuditingConfiguration.class, TestClockConfiguration.class, TestDatabaseConfiguration.class})
@TestPropertySource(locations = "classpath:jpa-test.properties")
public @interface JpaAdapterTest {}
