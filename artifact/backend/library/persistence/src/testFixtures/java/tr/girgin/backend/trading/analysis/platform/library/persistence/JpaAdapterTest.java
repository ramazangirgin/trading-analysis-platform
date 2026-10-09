package tr.girgin.backend.trading.analysis.platform.library.persistence;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * The JPA wiring of a persistence adapter test: Hibernate, Spring Data JPA repositories and
 * transactions as in the application, with the JPA settings of {@code jpa-test.properties}
 * ({@code ddl-auto=validate}, the naming strategy, global quoting), JPA auditing, and a
 * {@link MutableTestClock} as the {@code Clock} bean that a test injects to control the time. The test's own
 * configuration provides the data source (see {@link PostgresTestDatabase} and {@link TestMigrations}) and carries
 * {@code @AutoConfigurationPackage}, so entity and repository scan stay in its module.
 *
 * <p>No test-managed transaction: every call of a port runs in its own transaction, as in production.
 * This is not {@code @DataJpaTest}, whose per-test transaction would hide what the database sees.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ImportAutoConfiguration({
    HibernateJpaAutoConfiguration.class,
    DataJpaRepositoriesAutoConfiguration.class,
    TransactionAutoConfiguration.class
})
@Import({JpaAuditingConfiguration.class, TestClockConfiguration.class})
@TestPropertySource(locations = "classpath:jpa-test.properties")
public @interface JpaAdapterTest {}
