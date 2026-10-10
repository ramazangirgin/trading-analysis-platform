package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.PasswordHash;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.RoleRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.library.persistence.JpaAdapterTest;
import tr.girgin.backend.trading.analysis.platform.library.persistence.PostgresTestDatabase;

/**
 * V2 adds the optimistic-lock columns in place: the role, user and role assignment the test-only migration
 * {@code V1_1} (in {@code persistence/seed} of the test resources) stored before it are read back unchanged, at
 * version 0.
 */
@JpaAdapterTest
@SpringJUnitConfig(V2IdentityVersionMigrationTest.Config.class)
class V2IdentityVersionMigrationTest {

    private static final String PACKAGE_PATH = "classpath:"
            + IdentityPersistenceConfiguration.class.getPackageName().replace('.', '/');
    private static final RoleId ROLE = new RoleId("role_seed");
    private static final UserId USER = new UserId("u_seed");

    @Autowired
    private UserRepositoryPort users;

    @Autowired
    private RoleRepositoryPort roles;

    @Test
    void anExistingRoleKeepsEveryValueAndStartsAtVersionZero() {
        assertThat(roles.findById(ROLE))
                .contains(new Role(
                        ROLE,
                        "Seed role",
                        false,
                        "Stored before V2",
                        Set.of(Permission.PRESET_READ, Permission.USER_MANAGE),
                        0L));
    }

    @Test
    void anExistingUserKeepsEveryValueAndItsRoleAndStartsAtVersionZero() {
        assertThat(users.findById(USER))
                .contains(new User(
                        USER,
                        new Username("seed.user"),
                        new PasswordHash("{bcrypt}$2a$10$seed"),
                        true,
                        false,
                        2,
                        Optional.of(Instant.parse("2026-10-03T12:00:00Z")),
                        Instant.parse("2026-10-01T09:00:00.25Z"),
                        Instant.parse("2026-10-02T09:30:00Z"),
                        Set.of(ROLE),
                        0L));
    }

    @Test
    void aMigratedUserCanBeSavedAtItsVersion() {
        User seeded = users.findById(USER).orElseThrow();

        User saved = users.save(seeded.withRoleIds(Set.of()));

        assertThat(saved.version()).isEqualTo(1);
        assertThat(saved.roleIds()).isEmpty();
    }

    // Not a @Configuration, and the scan leaves every @Configuration out: the domain's own Flyway bean in
    // IdentityPersistenceConfiguration and the other tests' configurations. This test's Flyway runs the real
    // migrations with the seed between V1 and V2.
    @AutoConfigurationPackage
    @ComponentScan(
            basePackageClasses = JpaUserRepositoryAdapter.class,
            excludeFilters = @ComponentScan.Filter(Configuration.class))
    static class Config {

        @Bean
        DataSource dataSource() {
            return PostgresTestDatabase.create().dataSource();
        }

        @Bean(initMethod = "migrate")
        Flyway identityFlyway(DataSource dataSource) {
            return Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(IdentityPersistenceConfiguration.SCHEMA)
                    .createSchemas(true)
                    .table("FLYWAY_SCHEMA_HISTORY")
                    .locations(PACKAGE_PATH + "/migration", PACKAGE_PATH + "/seed")
                    .load();
        }
    }
}
