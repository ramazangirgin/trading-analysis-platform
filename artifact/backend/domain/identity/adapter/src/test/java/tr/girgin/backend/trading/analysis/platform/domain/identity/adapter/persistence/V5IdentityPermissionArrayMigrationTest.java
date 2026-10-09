package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.RoleRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.library.persistence.JpaAdapterTest;
import tr.girgin.backend.trading.analysis.platform.library.persistence.PostgresTestDatabase;
import tr.girgin.backend.trading.analysis.platform.library.persistence.TestMigrations;

/**
 * V5 moves the permissions of a role from ROLE_PERMISSIONS into an array on the role, in place: the
 * roles the test-only migration {@code V3_1} (in {@code db/seed/identity}) stored with one row per
 * permission key are read back with every permission.
 */
@JpaAdapterTest
@SpringJUnitConfig(V5IdentityPermissionArrayMigrationTest.Config.class)
class V5IdentityPermissionArrayMigrationTest {

    @Autowired
    private RoleRepositoryPort roles;

    @Autowired
    private UserRepositoryPort users;

    @Test
    void aRoleWithEveryPermissionKeyHasEveryPermission() {
        assertThat(roles.findById(new RoleId("r_seed_all")))
                .contains(new Role(
                        new RoleId("r_seed_all"),
                        "seed-all",
                        true,
                        "Every permission",
                        Set.of(Permission.values()),
                        0L));
        assertThat(roles.findById(new RoleId("r_seed_all")).orElseThrow().permissions())
                .containsExactlyInAnyOrderElementsOf(Arrays.asList(Permission.values()));
    }

    @Test
    void aRoleWithoutPermissionsGetsAnEmptyArray() {
        assertThat(roles.findByName("seed-none"))
                .contains(new Role(new RoleId("r_seed_none"), "seed-none", false, "No permission", Set.of(), 0L));
    }

    @Test
    void aRoleWithTwoPermissionsKeepsJustThose() {
        assertThat(roles.findByName("seed-viewer").orElseThrow().permissions())
                .containsExactlyInAnyOrder(Permission.ANALYSIS_READ, Permission.PRESET_READ);
    }

    @Test
    void theRoleAssignmentsOfAUserSurvive() {
        assertThat(users.findById(new UserId("u_seed_holder")).orElseThrow().roleIds())
                .containsExactly(new RoleId("r_seed_viewer"));
    }

    // Not a @Configuration: JpaIdentityRepositoryTest scans this package, and must not pick up this
    // data source.
    @AutoConfigurationPackage
    @ComponentScan(basePackageClasses = JpaUserRepositoryAdapter.class)
    static class Config {

        @Bean
        DataSource dataSource() {
            DataSource dataSource = PostgresTestDatabase.create().dataSource();
            // Only this domain's migrations: V1 and V2 belong to other modules.
            TestMigrations.migrate(dataSource, "2", "classpath:db/seed/identity");
            return dataSource;
        }
    }
}
