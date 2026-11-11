package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
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
import tr.girgin.backend.trading.analysis.platform.library.mapper.OptionalInstantToInstantMapper;
import tr.girgin.backend.trading.analysis.platform.library.persistence.JpaAdapterTest;
import tr.girgin.backend.trading.analysis.platform.library.persistence.MutableTestClock;

/** The repositories against a PostgreSQL database migrated by this domain's own Flyway bean. */
@JpaAdapterTest
@SpringJUnitConfig(JpaIdentityRepositoryTest.Config.class)
class JpaIdentityRepositoryTest {

    private static final Instant LOCKED_UNTIL = Instant.parse("2026-10-03T12:00:00Z");

    @Autowired
    private MutableTestClock clock;

    @Autowired
    private UserRepositoryPort userRepositoryPort;

    @Autowired
    private RoleRepositoryPort roleRepositoryPort;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @BeforeEach
    void resetClock() {
        clock.set(MutableTestClock.START);
    }

    @Test
    void userWithAllFieldsAndTwoRolesRoundTrips() {
        Role admin = saveRole("admin-roundtrip", Set.of(Permission.USER_MANAGE));
        Role viewer = saveRole("viewer-roundtrip", Set.of());
        User user = new User(
                new UserId(UUID.randomUUID().toString()),
                new Username("Round.Trip"),
                new PasswordHash("{bcrypt}$2a$10$hash"),
                false,
                true,
                3,
                Optional.of(LOCKED_UNTIL),
                Optional.empty(),
                null,
                null,
                Set.of(admin.id(), viewer.id()),
                null);

        User saved = userRepositoryPort.save(user);

        assertThat(saved).isEqualTo(audited(user, MutableTestClock.START, MutableTestClock.START, 0L));
        assertThat(userRepositoryPort.findById(user.id())).contains(saved);
    }

    @Test
    void userWithoutLockRoundTrips() {
        User user = user("no-lock", Set.of());

        User saved = userRepositoryPort.save(user);

        assertThat(userRepositoryPort.findById(user.id())).hasValueSatisfying(found -> {
            assertThat(found.lockedUntil()).isEmpty();
            assertThat(found).isEqualTo(saved);
        });
    }

    @Test
    void theFailedLoginCountAndTheLockRoundTripThroughASave() {
        User user = userRepositoryPort.save(user("lockout", Set.of()));

        User locked = userRepositoryPort.save(IdentityCopies.withFailedLogin(user, 4, Optional.of(LOCKED_UNTIL)));

        assertThat(userRepositoryPort.findById(user.id())).hasValueSatisfying(found -> {
            assertThat(found.failedLoginCount()).isEqualTo(4);
            assertThat(found.lockedUntil()).contains(LOCKED_UNTIL);
            assertThat(found).isEqualTo(locked);
        });
        User cleared = userRepositoryPort.save(IdentityCopies.withFailedLogin(locked, 0, Optional.empty()));
        assertThat(userRepositoryPort.findById(user.id())).hasValueSatisfying(found -> {
            assertThat(found.failedLoginCount()).isZero();
            assertThat(found.lockedUntil()).isEmpty();
            assertThat(found).isEqualTo(cleared);
        });
    }

    @Test
    void theLastLoginRoundTripsAsNullAndAsATime() {
        User saved = userRepositoryPort.save(user("last-login", Set.of()));
        assertThat(userRepositoryPort.findById(saved.id()).orElseThrow().lastLoginAt())
                .isEmpty();

        Instant at = Instant.parse("2026-10-04T08:30:00Z");
        User loggedIn = userRepositoryPort.save(IdentityCopies.withLastLogin(saved, at));

        assertThat(loggedIn.lastLoginAt()).contains(at);
        assertThat(userRepositoryPort.findById(saved.id())).contains(loggedIn);
    }

    @Test
    void countEnabledWithRoleCountsOnlyEnabledUsersWithThatRole() {
        Role role = saveRole("count-role", Set.of());
        Role other = saveRole("count-other-role", Set.of());
        userRepositoryPort.save(user("count-role-enabled-one", Set.of(role.id())));
        userRepositoryPort.save(user("count-role-enabled-two", Set.of(role.id(), other.id())));
        userRepositoryPort.save(IdentityCopies.withEnabled(user("count-role-disabled", Set.of(role.id())), false));
        userRepositoryPort.save(user("count-role-other", Set.of(other.id())));

        assertThat(userRepositoryPort.countEnabledWithRole(role.id())).isEqualTo(2);
        assertThat(userRepositoryPort.countEnabledWithRole(other.id())).isEqualTo(2);
        assertThat(userRepositoryPort.countEnabledWithRole(IdentityCopies.newRoleId()))
                .isZero();
    }

    @Test
    void aNewUserGetsTheTimeOfTheClockAsCreatedAtAndUpdatedAt() {
        User saved = userRepositoryPort.save(user("new-audit", Set.of()));

        assertThat(saved.createdAt()).isEqualTo(MutableTestClock.START);
        assertThat(saved.updatedAt()).isEqualTo(MutableTestClock.START);
        User found = userRepositoryPort.findById(saved.id()).orElseThrow();
        assertThat(found.createdAt()).isEqualTo(MutableTestClock.START);
        assertThat(found.updatedAt()).isEqualTo(MutableTestClock.START);
    }

    @Test
    void updatingAUserReplacesItsFieldsAndRoleAssignments() {
        Role first = saveRole("first-update", Set.of());
        Role second = saveRole("second-update", Set.of());
        User user = userRepositoryPort.save(user("update-me", Set.of(first.id())));
        clock.advance(Duration.ofHours(1));
        User changed = new User(
                user.id(),
                new Username("Update-Me"),
                new PasswordHash("{bcrypt}$2a$10$other"),
                false,
                true,
                5,
                Optional.of(LOCKED_UNTIL),
                Optional.empty(),
                null,
                null,
                Set.of(second.id()),
                user.version());

        User saved = userRepositoryPort.save(changed);

        Instant later = MutableTestClock.START.plus(Duration.ofHours(1));
        assertThat(saved).isEqualTo(audited(changed, MutableTestClock.START, later, 1L));
        assertThat(userRepositoryPort.findById(user.id())).contains(saved);
        assertThat(userRepositoryPort.findAll())
                .filteredOn(found -> found.id().equals(user.id()))
                .containsExactly(saved);
    }

    @Test
    void aResaveKeepsCreatedAtAndMovesUpdatedAt() {
        User first = userRepositoryPort.save(user("keep-created", Set.of()));
        clock.advance(Duration.ofMinutes(10));

        User resaved = userRepositoryPort.save(first);

        Instant later = MutableTestClock.START.plus(Duration.ofMinutes(10));
        assertThat(resaved.createdAt()).isEqualTo(MutableTestClock.START);
        assertThat(resaved.updatedAt()).isEqualTo(later);
        User found = userRepositoryPort.findById(first.id()).orElseThrow();
        assertThat(found.createdAt()).isEqualTo(MutableTestClock.START);
        assertThat(found.updatedAt()).isEqualTo(later);
    }

    @Test
    void aResaveThatOnlyChangesTheRoleAssignmentsMovesUpdatedAt() {
        Role role = saveRole("only-roles", Set.of());
        User first = userRepositoryPort.save(user("only-role-change", Set.of()));
        clock.advance(Duration.ofMinutes(10));

        User resaved = userRepositoryPort.save(IdentityCopies.withRoleIds(first, Set.of(role.id())));

        Instant later = MutableTestClock.START.plus(Duration.ofMinutes(10));
        assertThat(resaved.roleIds()).containsExactly(role.id());
        assertThat(resaved.updatedAt()).isEqualTo(later);
        assertThat(userRepositoryPort.findById(first.id()).orElseThrow().updatedAt())
                .isEqualTo(later);
    }

    @Test
    void findByUsernameMatchesInAnyCase() {
        User user = userRepositoryPort.save(user("MixedCase.User", Set.of()));

        assertThat(userRepositoryPort.findByUsername(new Username("mixedcase.user")))
                .contains(user);
        assertThat(userRepositoryPort.findByUsername(new Username("MIXEDCASE.USER")))
                .contains(user);
        assertThat(userRepositoryPort.findByUsername(new Username("mixedcase.other")))
                .isEmpty();
    }

    @Test
    void aSecondUserWithTheSameUsernameInAnotherCaseIsRejected() {
        userRepositoryPort.save(user("Taken.Name", Set.of()));

        assertThatThrownBy(() -> userRepositoryPort.save(user("taken.name", Set.of())))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void renamingAUserToATakenUsernameIsRejected() {
        userRepositoryPort.save(user("Held.Name", Set.of()));
        User other = userRepositoryPort.save(user("other.name", Set.of()));

        assertThatThrownBy(() -> userRepositoryPort.save(new User(
                        other.id(),
                        new Username("held.name"),
                        other.passwordHash(),
                        other.enabled(),
                        other.mustChangePassword(),
                        other.failedLoginCount(),
                        other.lockedUntil(),
                        other.lastLoginAt(),
                        null,
                        null,
                        other.roleIds(),
                        other.version())))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void aNewUserStartsAtVersionZeroAndEveryWriteAddsOne() {
        User first = userRepositoryPort.save(user("version-steps", Set.of()));
        User second = userRepositoryPort.save(first);

        assertThat(first.version()).isZero();
        assertThat(second.version()).isEqualTo(1);
        assertThat(userRepositoryPort.findById(first.id()).orElseThrow().version())
                .isEqualTo(1);
    }

    @Test
    void aStaleUserWriteFailsAndLeavesTheNewerRowUnchanged() {
        Role role = saveRole("stale-user-role", Set.of());
        User saved = userRepositoryPort.save(user("stale-user", Set.of()));
        User firstCopy = userRepositoryPort.findById(saved.id()).orElseThrow();
        User staleCopy = userRepositoryPort.findById(saved.id()).orElseThrow();
        User firstWrite = userRepositoryPort.save(IdentityCopies.withRoleIds(firstCopy, Set.of(role.id())));

        assertThat(firstWrite.version()).isEqualTo(1);
        assertThatThrownBy(() -> userRepositoryPort.save(IdentityCopies.withRoleIds(staleCopy, Set.of())))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(userRepositoryPort.findById(saved.id())).contains(firstWrite);
        assertThat(userRepositoryPort.findById(saved.id()).orElseThrow().roleIds())
                .containsExactly(role.id());
    }

    @Test
    void savingAUserWithoutAVersionFailsOnAnExistingId() {
        User saved = userRepositoryPort.save(user("no-version-user", Set.of()));

        assertThatThrownBy(() -> userRepositoryPort.save(user(saved, null)))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(userRepositoryPort.findById(saved.id())).contains(saved);
    }

    @Test
    void savingAUserVersionForADeletedRowFails() {
        User saved = userRepositoryPort.save(user("deleted-user", Set.of()));
        userJpaRepository.deleteById(userKey(saved.id()));

        assertThatThrownBy(() -> userRepositoryPort.save(saved)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(userRepositoryPort.findById(saved.id())).isEmpty();
    }

    @Test
    void aStaleRoleWriteFailsAndLeavesTheNewerRowUnchanged() {
        Role saved = saveRole("stale-role", Set.of(Permission.ANALYSIS_READ));
        Role firstCopy = roleRepositoryPort.findById(saved.id()).orElseThrow();
        Role staleCopy = roleRepositoryPort.findById(saved.id()).orElseThrow();
        Role firstWrite =
                roleRepositoryPort.save(IdentityCopies.withPermissions(firstCopy, Set.of(Permission.AUDIT_READ)));

        assertThat(saved.version()).isZero();
        assertThat(firstWrite.version()).isEqualTo(1);
        assertThatThrownBy(() -> roleRepositoryPort.save(
                        IdentityCopies.withPermissions(staleCopy, Set.of(Permission.USER_READ))))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(roleRepositoryPort.findById(saved.id())).contains(firstWrite);
    }

    @Test
    void savingARoleWithoutAVersionFailsOnAnExistingId() {
        Role saved = saveRole("no-version-role", Set.of());

        assertThatThrownBy(() -> roleRepositoryPort.save(new Role(saved.id(), "other", false, "", Set.of(), null)))
                .isInstanceOf(DataAccessException.class);

        assertThat(roleRepositoryPort.findById(saved.id())).contains(saved);
    }

    @Test
    void countAndFindAllSeeEveryUser() {
        long before = userRepositoryPort.count();
        userRepositoryPort.save(user("count-one", Set.of()));
        userRepositoryPort.save(user("count-two", Set.of()));

        assertThat(userRepositoryPort.count()).isEqualTo(before + 2);
        assertThat(userRepositoryPort.findAll()).hasSize((int) before + 2);
    }

    @Test
    void roleWithPermissionsRoundTripsAndASaveReplacesThem() {
        Role role = saveRole(
                "analyst-permissions",
                Set.of(Permission.ANALYSIS_RUN, Permission.ANALYSIS_READ, Permission.PRESET_READ));
        assertThat(roleRepositoryPort.findById(role.id())).contains(role);

        Role changed = roleRepositoryPort.save(new Role(
                role.id(),
                role.name(),
                true,
                "now built in",
                Set.of(Permission.ANALYSIS_READ, Permission.AUDIT_READ),
                role.version()));

        assertThat(changed.version()).isEqualTo(1);
        assertThat(roleRepositoryPort.findById(role.id())).contains(changed);
        assertThat(roleRepositoryPort.findAll()).contains(changed).doesNotContain(role);
    }

    @Test
    void everyPermissionRoundTrips() {
        Role role = saveRole("every-permission", new HashSet<>(Arrays.asList(Permission.values())));

        assertThat(roleRepositoryPort.findById(role.id()).orElseThrow().permissions())
                .containsExactlyInAnyOrder(Permission.values());
    }

    @Test
    void findByNameFindsTheRole() {
        Role role = saveRole("find-by-name", Set.of(Permission.SETTINGS_READ));

        assertThat(roleRepositoryPort.findByName("find-by-name")).contains(role);
        assertThat(roleRepositoryPort.findByName("no-such-role")).isEmpty();
    }

    @Test
    void findByNameIgnoresCase() {
        Role role = saveRole("Mixed Case Role", Set.of());

        assertThat(roleRepositoryPort.findByName("mixed case role")).contains(role);
        assertThat(roleRepositoryPort.findByName("MIXED CASE ROLE")).contains(role);
    }

    @Test
    void aSecondRoleWhoseNameDiffersOnlyInCaseIsRejected() {
        saveRole("Taken Role Name", Set.of());

        assertThatThrownBy(() -> saveRole("taken role name", Set.of())).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void renamingARoleToATakenNameInAnotherCaseIsRejected() {
        saveRole("Held Role Name", Set.of());
        Role other = saveRole("other role name", Set.of());

        assertThatThrownBy(() -> roleRepositoryPort.save(IdentityCopies.withName(other, "HELD ROLE NAME")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void aRoleStillAssignedToAUserCannotBeDeleted() {
        Role role = saveRole("assigned-role", Set.of(Permission.ANALYSIS_RUN));
        userRepositoryPort.save(user("role-holder", Set.of(role.id())));

        assertThatThrownBy(() -> roleRepositoryPort.deleteById(role.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(roleRepositoryPort.findById(role.id())).contains(role);
    }

    @Test
    void aRoleWithoutUsersIsDeleted() {
        Role role = saveRole("deletable-role", Set.of());

        roleRepositoryPort.deleteById(role.id());

        assertThat(roleRepositoryPort.findById(role.id())).isEmpty();
    }

    @Test
    void countWithRoleAndCountUsersByRoleCountEnabledAndDisabledUsers() {
        Role role = saveRole("all-count-role", Set.of());
        Role other = saveRole("all-count-other-role", Set.of());
        Role unused = saveRole("all-count-unused-role", Set.of());
        userRepositoryPort.save(user("all-count-enabled", Set.of(role.id())));
        userRepositoryPort.save(
                IdentityCopies.withEnabled(user("all-count-disabled", Set.of(role.id(), other.id())), false));

        assertThat(userRepositoryPort.countWithRole(role.id())).isEqualTo(2);
        assertThat(userRepositoryPort.countWithRole(other.id())).isEqualTo(1);
        assertThat(userRepositoryPort.countWithRole(unused.id())).isZero();
        assertThat(userRepositoryPort.countUsersByRole())
                .containsEntry(role.id(), 2L)
                .containsEntry(other.id(), 1L)
                .doesNotContainKey(unused.id());
    }

    @Test
    void deletingAUserRemovesItsAssignmentsButNotTheRole() {
        Role role = saveRole("kept-role", Set.of());
        User user = user("goes-away", Set.of(role.id()));
        userRepositoryPort.save(user);

        userJpaRepository.deleteById(userKey(user.id()));

        assertThat(userRepositoryPort.findById(user.id())).isEmpty();
        assertThat(roleRepositoryPort.findById(role.id())).isPresent();
    }

    @Test
    void aFailingSaveLeavesNoPartialAssignment() {
        Role known = saveRole("known-role", Set.of());
        User user = user("half-written", Set.of(known.id(), IdentityCopies.newRoleId()));

        assertThatThrownBy(() -> userRepositoryPort.save(user)).isInstanceOf(DataAccessException.class);

        assertThat(userRepositoryPort.findById(user.id())).isEmpty();
    }

    @Test
    void theMigrationsSeedTheBuiltInAdminRoleWithEveryPermission() {
        assertThat(roleRepositoryPort.findById(RoleId.ADMIN)).hasValueSatisfying(admin -> {
            assertThat(admin.name()).isEqualTo("ADMIN");
            assertThat(admin.builtIn()).isTrue();
            assertThat(admin.permissions()).containsExactlyInAnyOrder(Permission.values());
            assertThat(admin.version()).isZero();
        });
    }

    @Test
    void aUserWithTheAdminRoleRoundTrips() {
        User saved = userRepositoryPort.save(user("seeded-admin-user", Set.of(RoleId.ADMIN)));

        assertThat(userRepositoryPort.findById(saved.id())).contains(saved);
        assertThat(saved.roleIds()).containsExactly(RoleId.ADMIN);
    }

    @Test
    void theMigrationsSeedTheBuiltInAnalystRole() {
        assertThat(roleRepositoryPort.findById(RoleId.ANALYST)).hasValueSatisfying(analyst -> {
            assertThat(analyst.name()).isEqualTo("ANALYST");
            assertThat(analyst.builtIn()).isTrue();
            assertThat(analyst.permissions())
                    .containsExactlyInAnyOrder(
                            Permission.ANALYSIS_RUN,
                            Permission.ANALYSIS_READ,
                            Permission.ANALYSIS_DELETE,
                            Permission.PRESET_READ,
                            Permission.PRESET_MANAGE,
                            Permission.SETTINGS_READ);
        });
    }

    @Test
    void theMigrationsSeedTheBuiltInViewerRole() {
        assertThat(roleRepositoryPort.findById(RoleId.VIEWER)).hasValueSatisfying(viewer -> {
            assertThat(viewer.name()).isEqualTo("VIEWER");
            assertThat(viewer.builtIn()).isTrue();
            assertThat(viewer.permissions())
                    .containsExactlyInAnyOrder(Permission.ANALYSIS_READ, Permission.PRESET_READ);
        });
    }

    @Test
    void theMigrationsSeedTheBuiltInPublisherRoleWithOnlyAnalysisShare() {
        assertThat(roleRepositoryPort.findById(RoleId.PUBLISHER)).hasValueSatisfying(publisher -> {
            assertThat(publisher.name()).isEqualTo("PUBLISHER");
            assertThat(publisher.builtIn()).isTrue();
            assertThat(publisher.permissions()).containsExactly(Permission.ANALYSIS_SHARE);
        });
    }

    @Test
    void onlyAdminAndPublisherHoldAnalysisShare() {
        assertThat(roleRepositoryPort.findById(RoleId.ADMIN).orElseThrow().permissions())
                .contains(Permission.ANALYSIS_SHARE);
        assertThat(roleRepositoryPort.findById(RoleId.ANALYST).orElseThrow().permissions())
                .doesNotContain(Permission.ANALYSIS_SHARE);
        assertThat(roleRepositoryPort.findById(RoleId.VIEWER).orElseThrow().permissions())
                .doesNotContain(Permission.ANALYSIS_SHARE);
    }

    @Test
    void aUserWithTheAnalystOrViewerRoleRoundTrips() {
        User saved = userRepositoryPort.save(user("seeded-analyst-viewer-user", Set.of(RoleId.ANALYST, RoleId.VIEWER)));

        assertThat(userRepositoryPort.findById(saved.id())).contains(saved);
        assertThat(saved.roleIds()).containsExactlyInAnyOrder(RoleId.ANALYST, RoleId.VIEWER);
    }

    private Role saveRole(String name, Set<Permission> permissions) {
        return roleRepositoryPort.save(new Role(IdentityCopies.newRoleId(), name, false, "", permissions, null));
    }

    private static User user(String username, Set<RoleId> roleIds) {
        return new User(
                new UserId(UUID.randomUUID().toString()),
                new Username(username),
                new PasswordHash("{bcrypt}$2a$10$hash"),
                true,
                false,
                0,
                Optional.empty(),
                Optional.empty(),
                null,
                null,
                roleIds,
                null);
    }

    private static User user(User user, Long version) {
        return audited(user, user.createdAt(), user.updatedAt(), version);
    }

    private static User audited(User user, Instant createdAt, Instant updatedAt, Long version) {
        return new User(
                user.id(),
                user.username(),
                user.passwordHash(),
                user.enabled(),
                user.mustChangePassword(),
                user.failedLoginCount(),
                user.lockedUntil(),
                user.lastLoginAt(),
                createdAt,
                updatedAt,
                user.roleIds(),
                version);
    }

    private static UserIdEmbeddable userKey(UserId id) {
        UserIdEmbeddable key = new UserIdEmbeddable();
        key.setValue(id.value());
        return key;
    }

    @Configuration
    @AutoConfigurationPackage
    @ComponentScan(basePackageClasses = {JpaIdentityRepositoryTest.class, OptionalInstantToInstantMapper.class})
    static class Config {}
}
