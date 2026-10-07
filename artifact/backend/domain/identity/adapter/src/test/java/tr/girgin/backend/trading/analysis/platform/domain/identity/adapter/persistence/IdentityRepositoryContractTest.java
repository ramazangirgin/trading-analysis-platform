package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper.UserRowToUserMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.PasswordHash;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.RoleRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;

/**
 * The repository cases, run against each database by a subclass that supplies the {@link DataSource}
 * (migrated with this domain's Flyway migration) in its own configuration.
 */
abstract class IdentityRepositoryContractTest {

    private static final Instant CREATED = Instant.parse("2026-10-01T10:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-10-02T11:30:15.250Z");
    private static final Instant LOCKED_UNTIL = Instant.parse("2026-10-03T12:00:00Z");

    @Autowired
    private UserRepositoryPort users;

    @Autowired
    private RoleRepositoryPort roles;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void userWithAllFieldsAndTwoRolesRoundTrips() {
        Role admin = saveRole("admin-roundtrip", Set.of(Permission.USER_MANAGE));
        Role viewer = saveRole("viewer-roundtrip", Set.of());
        User user = new User(
                UserId.newId(),
                new Username("Round.Trip"),
                new PasswordHash("{bcrypt}$2a$10$hash"),
                false,
                true,
                3,
                Optional.of(LOCKED_UNTIL),
                CREATED,
                UPDATED,
                Set.of(admin.id(), viewer.id()));

        users.save(user);

        assertThat(users.findById(user.id())).contains(user);
    }

    @Test
    void userWithoutLockRoundTrips() {
        User user = user("no-lock", Set.of());

        users.save(user);

        assertThat(users.findById(user.id())).hasValueSatisfying(found -> {
            assertThat(found.lockedUntil()).isEmpty();
            assertThat(found).isEqualTo(user);
        });
    }

    @Test
    void updatingAUserReplacesItsFieldsAndRoleAssignments() {
        Role first = saveRole("first-update", Set.of());
        Role second = saveRole("second-update", Set.of());
        User user = user("update-me", Set.of(first.id()));
        users.save(user);
        User changed = new User(
                user.id(),
                new Username("Update-Me"),
                new PasswordHash("{bcrypt}$2a$10$other"),
                false,
                true,
                5,
                Optional.of(LOCKED_UNTIL),
                CREATED,
                UPDATED,
                Set.of(second.id()));

        users.save(changed);

        assertThat(users.findById(user.id())).contains(changed);
        assertThat(users.findAll())
                .filteredOn(found -> found.id().equals(user.id()))
                .containsExactly(changed);
    }

    @Test
    void updatingAUserKeepsTheCreationTimeOfTheFirstSave() {
        User user = user("keep-created", Set.of());
        users.save(user);
        User resaved = new User(
                user.id(),
                user.username(),
                user.passwordHash(),
                user.enabled(),
                user.mustChangePassword(),
                user.failedLoginCount(),
                user.lockedUntil(),
                CREATED.plusSeconds(3600),
                UPDATED,
                user.roleIds());

        users.save(resaved);

        assertThat(users.findById(user.id()).orElseThrow().createdAt()).isEqualTo(user.createdAt());
    }

    @Test
    void findByUsernameMatchesInAnyCase() {
        User user = user("MixedCase.User", Set.of());
        users.save(user);

        assertThat(users.findByUsername(new Username("mixedcase.user"))).contains(user);
        assertThat(users.findByUsername(new Username("MIXEDCASE.USER"))).contains(user);
        assertThat(users.findByUsername(new Username("mixedcase.other"))).isEmpty();
    }

    @Test
    void aSecondUserWithTheSameUsernameInAnotherCaseIsRejected() {
        users.save(user("Taken.Name", Set.of()));

        assertThatThrownBy(() -> users.save(user("taken.name", Set.of()))).isInstanceOf(DataAccessException.class);
    }

    @Test
    void countAndFindAllSeeEveryUser() {
        long before = users.count();
        users.save(user("count-one", Set.of()));
        users.save(user("count-two", Set.of()));

        assertThat(users.count()).isEqualTo(before + 2);
        assertThat(users.findAll()).hasSize((int) before + 2);
    }

    @Test
    void roleWithPermissionsRoundTripsAndASaveReplacesThem() {
        Role role = saveRole(
                "analyst-permissions",
                Set.of(Permission.ANALYSIS_RUN, Permission.ANALYSIS_READ, Permission.PRESET_READ));
        assertThat(roles.findById(role.id())).contains(role);

        Role changed = new Role(
                role.id(), role.name(), true, "now built in", Set.of(Permission.ANALYSIS_READ, Permission.AUDIT_READ));
        roles.save(changed);

        assertThat(roles.findById(role.id())).contains(changed);
        assertThat(roles.findAll()).contains(changed).doesNotContain(role);
    }

    @Test
    void findByNameFindsTheRole() {
        Role role = saveRole("find-by-name", Set.of(Permission.SETTINGS_READ));

        assertThat(roles.findByName("find-by-name")).contains(role);
        assertThat(roles.findByName("no-such-role")).isEmpty();
    }

    @Test
    void aRoleStillAssignedToAUserCannotBeDeleted() {
        Role role = saveRole("assigned-role", Set.of(Permission.ANALYSIS_RUN));
        users.save(user("role-holder", Set.of(role.id())));

        assertThatThrownBy(() -> jdbc.sql("DELETE FROM roles WHERE id = :id")
                        .param("id", role.id().value())
                        .update())
                .isInstanceOf(DataAccessException.class);
        assertThat(roles.findById(role.id())).contains(role);
    }

    @Test
    void deletingAUserRemovesItsAssignmentsButNotTheRole() {
        Role role = saveRole("kept-role", Set.of());
        User user = user("goes-away", Set.of(role.id()));
        users.save(user);

        jdbc.sql("DELETE FROM users WHERE id = :id")
                .param("id", user.id().value())
                .update();

        assertThat(users.findById(user.id())).isEmpty();
        assertThat(roles.findById(role.id())).isPresent();
    }

    @Test
    void aFailingSaveLeavesNoPartialAssignment() {
        Role known = saveRole("known-role", Set.of());
        User user = user("half-written", Set.of(known.id(), RoleId.newId()));

        assertThatThrownBy(() -> users.save(user)).isInstanceOf(DataAccessException.class);

        assertThat(users.findById(user.id())).isEmpty();
        assertThat(jdbc.sql("SELECT count(*) FROM user_roles WHERE user_id = :id")
                        .param("id", user.id().value())
                        .query(Long.class)
                        .single())
                .isZero();
    }

    private Role saveRole(String name, Set<Permission> permissions) {
        Role role = new Role(RoleId.newId(), name, false, "", permissions);
        roles.save(role);
        return role;
    }

    private static User user(String username, Set<RoleId> roleIds) {
        return new User(
                UserId.newId(),
                new Username(username),
                new PasswordHash("{bcrypt}$2a$10$hash"),
                true,
                false,
                0,
                Optional.empty(),
                CREATED,
                UPDATED,
                roleIds);
    }

    /** The beans every database setup shares; a subclass's configuration extends it and adds the data source. */
    @Configuration
    @EnableTransactionManagement
    @Import({JdbcUserRepositoryAdapter.class, JdbcRoleRepositoryAdapter.class})
    @ComponentScan(basePackageClasses = UserRowToUserMapper.class)
    abstract static class BaseConfig {

        @Bean
        JdbcClient jdbcClient(DataSource dataSource) {
            return JdbcClient.create(dataSource);
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
