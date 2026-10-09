package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.entity.RoleIdEmbeddable;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.entity.UserIdEmbeddable;
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
import tr.girgin.backend.trading.analysis.platform.library.persistence.MutableTestClock;
import tr.girgin.backend.trading.analysis.platform.library.persistence.PostgresTestDatabase;

/** The repositories against a PostgreSQL database migrated by this domain's own Flyway bean. */
@JpaAdapterTest
@SpringJUnitConfig(JpaIdentityRepositoryTest.Config.class)
class JpaIdentityRepositoryTest {

    private static final Instant LOCKED_UNTIL = Instant.parse("2026-10-03T12:00:00Z");

    @Autowired
    private MutableTestClock clock;

    @Autowired
    private UserRepositoryPort users;

    @Autowired
    private RoleRepositoryPort roles;

    @Autowired
    private UserJpaRepository userRepository;

    @Autowired
    private RoleJpaRepository roleRepository;

    @BeforeEach
    void resetClock() {
        clock.set(MutableTestClock.START);
    }

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
                null,
                null,
                Set.of(admin.id(), viewer.id()));

        User saved = users.save(user);

        assertThat(saved).isEqualTo(audited(user, MutableTestClock.START, MutableTestClock.START));
        assertThat(users.findById(user.id())).contains(saved);
    }

    @Test
    void userWithoutLockRoundTrips() {
        User user = user("no-lock", Set.of());

        User saved = users.save(user);

        assertThat(users.findById(user.id())).hasValueSatisfying(found -> {
            assertThat(found.lockedUntil()).isEmpty();
            assertThat(found).isEqualTo(saved);
        });
    }

    @Test
    void aNewUserGetsTheTimeOfTheClockAsCreatedAtAndUpdatedAt() {
        User saved = users.save(user("new-audit", Set.of()));

        assertThat(saved.createdAt()).isEqualTo(MutableTestClock.START);
        assertThat(saved.updatedAt()).isEqualTo(MutableTestClock.START);
        User found = users.findById(saved.id()).orElseThrow();
        assertThat(found.createdAt()).isEqualTo(MutableTestClock.START);
        assertThat(found.updatedAt()).isEqualTo(MutableTestClock.START);
    }

    @Test
    void updatingAUserReplacesItsFieldsAndRoleAssignments() {
        Role first = saveRole("first-update", Set.of());
        Role second = saveRole("second-update", Set.of());
        User user = user("update-me", Set.of(first.id()));
        users.save(user);
        clock.advance(Duration.ofHours(1));
        User changed = new User(
                user.id(),
                new Username("Update-Me"),
                new PasswordHash("{bcrypt}$2a$10$other"),
                false,
                true,
                5,
                Optional.of(LOCKED_UNTIL),
                null,
                null,
                Set.of(second.id()));

        User saved = users.save(changed);

        Instant later = MutableTestClock.START.plus(Duration.ofHours(1));
        assertThat(saved).isEqualTo(audited(changed, MutableTestClock.START, later));
        assertThat(users.findById(user.id())).contains(saved);
        assertThat(users.findAll())
                .filteredOn(found -> found.id().equals(user.id()))
                .containsExactly(saved);
    }

    @Test
    void aResaveKeepsCreatedAtAndMovesUpdatedAt() {
        User first = users.save(user("keep-created", Set.of()));
        clock.advance(Duration.ofMinutes(10));

        User resaved = users.save(first);

        Instant later = MutableTestClock.START.plus(Duration.ofMinutes(10));
        assertThat(resaved.createdAt()).isEqualTo(MutableTestClock.START);
        assertThat(resaved.updatedAt()).isEqualTo(later);
        User found = users.findById(first.id()).orElseThrow();
        assertThat(found.createdAt()).isEqualTo(MutableTestClock.START);
        assertThat(found.updatedAt()).isEqualTo(later);
    }

    @Test
    void aResaveThatOnlyChangesTheRoleAssignmentsMovesUpdatedAt() {
        Role role = saveRole("only-roles", Set.of());
        User first = users.save(user("only-role-change", Set.of()));
        clock.advance(Duration.ofMinutes(10));

        User resaved = users.save(first.withRoleIds(Set.of(role.id())));

        Instant later = MutableTestClock.START.plus(Duration.ofMinutes(10));
        assertThat(resaved.roleIds()).containsExactly(role.id());
        assertThat(resaved.updatedAt()).isEqualTo(later);
        assertThat(users.findById(first.id()).orElseThrow().updatedAt()).isEqualTo(later);
    }

    @Test
    void findByUsernameMatchesInAnyCase() {
        User user = users.save(user("MixedCase.User", Set.of()));

        assertThat(users.findByUsername(new Username("mixedcase.user"))).contains(user);
        assertThat(users.findByUsername(new Username("MIXEDCASE.USER"))).contains(user);
        assertThat(users.findByUsername(new Username("mixedcase.other"))).isEmpty();
    }

    @Test
    void aSecondUserWithTheSameUsernameInAnotherCaseIsRejected() {
        users.save(user("Taken.Name", Set.of()));

        assertThatThrownBy(() -> users.save(user("taken.name", Set.of()))).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void renamingAUserToATakenUsernameIsRejected() {
        users.save(user("Held.Name", Set.of()));
        User other = users.save(user("other.name", Set.of()));

        assertThatThrownBy(() -> users.save(new User(
                        other.id(),
                        new Username("held.name"),
                        other.passwordHash(),
                        other.enabled(),
                        other.mustChangePassword(),
                        other.failedLoginCount(),
                        other.lockedUntil(),
                        null,
                        null,
                        other.roleIds())))
                .isInstanceOf(DuplicateKeyException.class);
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
    void everyPermissionRoundTrips() {
        Role role = saveRole("every-permission", new HashSet<>(Arrays.asList(Permission.values())));

        assertThat(roles.findById(role.id()).orElseThrow().permissions())
                .containsExactlyInAnyOrder(Permission.values());
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

        assertThatThrownBy(() -> roleRepository.deleteById(roleKey(role.id())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(roles.findById(role.id())).contains(role);
    }

    @Test
    void deletingAUserRemovesItsAssignmentsButNotTheRole() {
        Role role = saveRole("kept-role", Set.of());
        User user = user("goes-away", Set.of(role.id()));
        users.save(user);

        userRepository.deleteById(userKey(user.id()));

        assertThat(users.findById(user.id())).isEmpty();
        assertThat(roles.findById(role.id())).isPresent();
    }

    @Test
    void aFailingSaveLeavesNoPartialAssignment() {
        Role known = saveRole("known-role", Set.of());
        User user = user("half-written", Set.of(known.id(), RoleId.newId()));

        assertThatThrownBy(() -> users.save(user)).isInstanceOf(DataAccessException.class);

        assertThat(users.findById(user.id())).isEmpty();
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
                null,
                null,
                roleIds);
    }

    private static User audited(User user, Instant createdAt, Instant updatedAt) {
        return new User(
                user.id(),
                user.username(),
                user.passwordHash(),
                user.enabled(),
                user.mustChangePassword(),
                user.failedLoginCount(),
                user.lockedUntil(),
                createdAt,
                updatedAt,
                user.roleIds());
    }

    private static RoleIdEmbeddable roleKey(RoleId id) {
        RoleIdEmbeddable key = new RoleIdEmbeddable();
        key.setValue(id.value());
        return key;
    }

    private static UserIdEmbeddable userKey(UserId id) {
        UserIdEmbeddable key = new UserIdEmbeddable();
        key.setValue(id.value());
        return key;
    }

    @Configuration
    @AutoConfigurationPackage
    @ComponentScan(basePackageClasses = JpaIdentityRepositoryTest.class)
    static class Config {

        @Bean
        DataSource dataSource() {
            return PostgresTestDatabase.create().dataSource();
        }
    }
}
