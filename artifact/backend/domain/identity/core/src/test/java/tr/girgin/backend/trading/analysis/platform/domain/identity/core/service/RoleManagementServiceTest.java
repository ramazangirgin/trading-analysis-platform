package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityError;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.RoleSummary;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;

class RoleManagementServiceTest {

    private final InMemoryRoleRepository roles = new InMemoryRoleRepository();
    private final FakeUserRepository users = new FakeUserRepository();
    private final RoleManagementService service = new RoleManagementService(roles, users);

    private Role admin;
    private Role analyst;
    private Role viewer;
    private Role custom;

    @BeforeEach
    void seedRoles() {
        admin = roles.add(new Role(RoleId.ADMIN, "ADMIN", true, "Everything", Set.of(Permission.values()), 0L));
        analyst = roles.add(
                new Role(RoleId.ANALYST, "ANALYST", true, "Runs analyses", Set.of(Permission.ANALYSIS_RUN), 0L));
        viewer = roles.add(new Role(RoleId.VIEWER, "VIEWER", true, "Reads", Set.of(Permission.ANALYSIS_READ), 0L));
        custom = roles.add(new Role(RoleId.newId(), "Auditor", false, "Audits", Set.of(Permission.AUDIT_READ), 3L));
    }

    @Test
    void listsTheRolesSortedByNameWithTheirUserCounts() {
        users.counts.put(RoleId.ANALYST, 2L);
        users.counts.put(custom.id(), 5L);

        assertThat(service.list())
                .containsExactly(
                        new RoleSummary(admin, 0),
                        new RoleSummary(analyst, 2),
                        new RoleSummary(custom, 5),
                        new RoleSummary(viewer, 0));
    }

    @Test
    void getsARoleWithItsUserCount() {
        users.counts.put(custom.id(), 4L);

        assertThat(service.get(custom.id())).isEqualTo(new RoleSummary(custom, 4));
    }

    @Test
    void getFailsForAnUnknownRole() {
        assertThatThrownBy(() -> service.get(RoleId.newId()))
                .satisfies(e -> assertError(e, IdentityError.ROLE_NOT_FOUND));
    }

    @Test
    void createsACustomRoleWithATrimmedNameAndDescription() {
        RoleSummary created = service.create("  Risk Desk  ", " Watches risk ", Set.of(Permission.ANALYSIS_READ));

        Role role = created.role();
        assertThat(role.name()).isEqualTo("Risk Desk");
        assertThat(role.description()).isEqualTo("Watches risk");
        assertThat(role.builtIn()).isFalse();
        assertThat(role.permissions()).containsExactly(Permission.ANALYSIS_READ);
        assertThat(created.userCount()).isZero();
        assertThat(roles.findById(role.id())).contains(role);
    }

    @Test
    void copiesThePermissionSetOfACreatedRoleAndKeepsItImmutable() {
        Set<Permission> permissions = new HashSet<>(Set.of(Permission.ANALYSIS_RUN));

        Role role = service.create("Runner", "", permissions).role();
        permissions.add(Permission.USER_MANAGE);

        assertThat(role.permissions()).containsExactly(Permission.ANALYSIS_RUN);
        assertThatThrownBy(() -> role.permissions().add(Permission.USER_MANAGE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void anUpdateReplacesOnlyTheNameKeepingTheOtherParts() {
        Role updated = service.update(custom.id(), Optional.of("Other"), Optional.empty(), Optional.empty(), 3L)
                .role();

        assertThat(updated)
                .isEqualTo(new Role(custom.id(), "Other", false, "Audits", Set.of(Permission.AUDIT_READ), 4L));
    }

    @Test
    void anUpdateReplacesOnlyTheDescriptionKeepingTheOtherParts() {
        Role updated = service.update(custom.id(), Optional.empty(), Optional.of("Other"), Optional.empty(), 3L)
                .role();

        assertThat(updated)
                .isEqualTo(new Role(custom.id(), "Auditor", false, "Other", Set.of(Permission.AUDIT_READ), 4L));
    }

    @Test
    void anUpdateReplacesOnlyThePermissionsKeepingTheOtherParts() {
        Role updated = service.update(
                        custom.id(), Optional.empty(), Optional.empty(), Optional.of(Set.of(Permission.USER_READ)), 3L)
                .role();

        assertThat(updated)
                .isEqualTo(new Role(custom.id(), "Auditor", false, "Audits", Set.of(Permission.USER_READ), 4L));
    }

    @Test
    void createsARoleWithoutPermissionsAndWithoutDescription() {
        Role role = service.create("Prepared", null, Set.of()).role();

        assertThat(role.permissions()).isEmpty();
        assertThat(role.description()).isEmpty();
    }

    @Test
    void createRejectsInvalidNames() {
        for (String name : new String[] {null, "", " ", "a", " a ", "x".repeat(41), "Rôle", "bad/name", "a.b"}) {
            assertThatThrownBy(() -> service.create(name, "", Set.of()))
                    .as("name %s", name)
                    .satisfies(e -> assertError(e, IdentityError.INVALID_ROLE_NAME));
        }
    }

    @Test
    void createAcceptsTheLongestNameAndDescription() {
        Role role = service.create("x".repeat(40), "d".repeat(200), Set.of()).role();

        assertThat(role.name()).hasSize(40);
        assertThat(role.description()).hasSize(200);
    }

    @Test
    void createRejectsATooLongDescription() {
        assertThatThrownBy(() -> service.create("Desk", "d".repeat(201), Set.of()))
                .satisfies(e -> assertError(e, IdentityError.INVALID_ROLE_DESCRIPTION));
    }

    @Test
    void createRejectsANameTakenInAnotherCase() {
        assertThatThrownBy(() -> service.create("admin", "", Set.of()))
                .satisfies(e -> assertError(e, IdentityError.ROLE_NAME_TAKEN))
                .satisfies(e -> assertThat(((IdentityException) e).params()).containsEntry("name", "admin"));
        assertThat(roles.saved()).isEmpty();
    }

    @Test
    void createTurnsADuplicateKeyFromThePortIntoNameTaken() {
        roles.failSavesWith(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() -> service.create("Desk", "", Set.of()))
                .satisfies(e -> assertError(e, IdentityError.ROLE_NAME_TAKEN));
    }

    @Test
    void renamesChangesTheDescriptionAndThePermissionsOfACustomRole() {
        RoleSummary updated = service.update(
                custom.id(),
                Optional.of(" Compliance "),
                Optional.of("Checks"),
                Optional.of(Set.of(Permission.AUDIT_READ, Permission.USER_READ)),
                3L);

        assertThat(updated.role())
                .isEqualTo(new Role(
                        custom.id(),
                        "Compliance",
                        false,
                        "Checks",
                        Set.of(Permission.AUDIT_READ, Permission.USER_READ),
                        4L));
        assertThat(roles.findById(custom.id())).contains(updated.role());
    }

    @Test
    void anEmptyArgumentLeavesItsPartUnchanged() {
        Role updated = service.update(custom.id(), Optional.empty(), Optional.empty(), Optional.of(Set.of()), null)
                .role();

        assertThat(updated.name()).isEqualTo("Auditor");
        assertThat(updated.description()).isEqualTo("Audits");
        assertThat(updated.permissions()).isEmpty();
    }

    @Test
    void updateReturnsTheUserCount() {
        users.counts.put(custom.id(), 2L);

        assertThat(service.update(custom.id(), Optional.of("Renamed"), Optional.empty(), Optional.empty(), null)
                        .userCount())
                .isEqualTo(2);
    }

    @Test
    void anUpdateThatChangesNothingWritesNothing() {
        RoleSummary result = service.update(
                custom.id(),
                Optional.of("Auditor"),
                Optional.of("Audits"),
                Optional.of(Set.of(Permission.AUDIT_READ)),
                3L);

        assertThat(result.role()).isEqualTo(custom);
        assertThat(roles.saved()).isEmpty();
    }

    @Test
    void anUpdateWithOnlyTheCurrentNameInAnotherCaseRenames() {
        Role updated = service.update(custom.id(), Optional.of("AUDITOR"), Optional.empty(), Optional.empty(), null)
                .role();

        assertThat(updated.name()).isEqualTo("AUDITOR");
    }

    @Test
    void updateRejectsAnInvalidNameAndDescription() {
        assertThatThrownBy(
                        () -> service.update(custom.id(), Optional.of("x"), Optional.empty(), Optional.empty(), null))
                .satisfies(e -> assertError(e, IdentityError.INVALID_ROLE_NAME));
        assertThatThrownBy(() -> service.update(
                        custom.id(), Optional.empty(), Optional.of("d".repeat(201)), Optional.empty(), null))
                .satisfies(e -> assertError(e, IdentityError.INVALID_ROLE_DESCRIPTION));
    }

    @Test
    void updateRejectsANameTakenByAnotherRoleInAnyCase() {
        assertThatThrownBy(() ->
                        service.update(custom.id(), Optional.of("viewer"), Optional.empty(), Optional.empty(), null))
                .satisfies(e -> assertError(e, IdentityError.ROLE_NAME_TAKEN));
    }

    @Test
    void updateTurnsADuplicateKeyFromThePortIntoNameTaken() {
        roles.failSavesWith(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() ->
                        service.update(custom.id(), Optional.of("Other"), Optional.empty(), Optional.empty(), null))
                .satisfies(e -> assertError(e, IdentityError.ROLE_NAME_TAKEN));
    }

    @Test
    void updateRejectsAStaleVersion() {
        assertThatThrownBy(
                        () -> service.update(custom.id(), Optional.of("Other"), Optional.empty(), Optional.empty(), 2L))
                .satisfies(e -> assertError(e, IdentityError.CONCURRENT_UPDATE))
                .hasMessage("Role " + custom.id().value() + " was changed by someone else");
        assertThat(roles.saved()).isEmpty();
    }

    @Test
    void updateTurnsAnOptimisticLockFailureOfTheSaveIntoConcurrentUpdate() {
        OptimisticLockingFailureException failure = new OptimisticLockingFailureException("stale");
        roles.failSavesWith(failure);

        assertThatThrownBy(
                        () -> service.update(custom.id(), Optional.of("Other"), Optional.empty(), Optional.empty(), 3L))
                .satisfies(e -> assertError(e, IdentityError.CONCURRENT_UPDATE))
                .hasCause(failure);
    }

    @Test
    void updateFailsForAnUnknownRole() {
        assertThatThrownBy(() ->
                        service.update(RoleId.newId(), Optional.of("Other"), Optional.empty(), Optional.empty(), null))
                .satisfies(e -> assertError(e, IdentityError.ROLE_NOT_FOUND));
    }

    @Test
    void aBuiltInRoleCannotBeRenamed() {
        for (Role builtIn : List.of(admin, analyst, viewer)) {
            assertThatThrownBy(() -> service.update(
                            builtIn.id(), Optional.of("Other"), Optional.empty(), Optional.empty(), null))
                    .satisfies(e -> assertError(e, IdentityError.BUILT_IN_ROLE))
                    .satisfies(e -> assertThat(((IdentityException) e).params())
                            .containsEntry("id", builtIn.id().value())
                            .containsEntry("change", "rename"));
        }
    }

    @Test
    void theDescriptionOfABuiltInRoleCannotChange() {
        assertThatThrownBy(() ->
                        service.update(RoleId.ANALYST, Optional.empty(), Optional.of("Other"), Optional.empty(), null))
                .satisfies(e -> assertError(e, IdentityError.BUILT_IN_ROLE))
                .satisfies(e -> assertThat(((IdentityException) e).params()).containsEntry("change", "description"));
    }

    @Test
    void thePermissionsOfAdminCannotChange() {
        assertThatThrownBy(() -> service.update(
                        RoleId.ADMIN,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(Set.of(Permission.USER_READ)),
                        null))
                .satisfies(e -> assertError(e, IdentityError.BUILT_IN_ROLE))
                .satisfies(e -> assertThat(((IdentityException) e).params()).containsEntry("change", "permissions"));
        assertThat(roles.saved()).isEmpty();
    }

    @Test
    void thePermissionsOfAnalystAndViewerCanChange() {
        Role changedAnalyst = service.update(
                        RoleId.ANALYST,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(Set.of(Permission.ANALYSIS_RUN, Permission.PRESET_READ)),
                        0L)
                .role();
        Role changedViewer = service.update(
                        RoleId.VIEWER, Optional.empty(), Optional.empty(), Optional.of(Set.of()), null)
                .role();

        assertThat(changedAnalyst.permissions())
                .containsExactlyInAnyOrder(Permission.ANALYSIS_RUN, Permission.PRESET_READ);
        assertThat(changedViewer.permissions()).isEmpty();
        assertThat(changedViewer.builtIn()).isTrue();
    }

    @Test
    void sendingTheCurrentNameOrDescriptionOfABuiltInRoleIsNotAChange() {
        RoleSummary result = service.update(
                RoleId.ADMIN,
                Optional.of("ADMIN"),
                Optional.of("Everything"),
                Optional.of(Set.of(Permission.values())),
                0L);

        assertThat(result.role()).isEqualTo(admin);
        assertThat(roles.saved()).isEmpty();
    }

    @Test
    void sendingTheCurrentNameOfABuiltInRoleWhileChangingItsPermissionsPasses() {
        Role updated = service.update(
                        RoleId.VIEWER,
                        Optional.of("VIEWER"),
                        Optional.empty(),
                        Optional.of(Set.of(Permission.PRESET_READ)),
                        null)
                .role();

        assertThat(updated.permissions()).containsExactly(Permission.PRESET_READ);
    }

    @Test
    void deletesACustomRoleWithoutUsers() {
        service.delete(custom.id());

        assertThat(roles.findById(custom.id())).isEmpty();
    }

    @Test
    void deleteFailsForAnUnknownRole() {
        assertThatThrownBy(() -> service.delete(RoleId.newId()))
                .satisfies(e -> assertError(e, IdentityError.ROLE_NOT_FOUND));
    }

    @Test
    void aBuiltInRoleCannotBeDeleted() {
        for (RoleId id : List.of(RoleId.ADMIN, RoleId.ANALYST, RoleId.VIEWER)) {
            assertThatThrownBy(() -> service.delete(id))
                    .satisfies(e -> assertError(e, IdentityError.BUILT_IN_ROLE))
                    .satisfies(e -> assertThat(((IdentityException) e).params()).containsEntry("change", "delete"));
        }
        assertThat(roles.deleted()).isEmpty();
    }

    @Test
    void aRoleHeldByUsersCannotBeDeleted() {
        users.counts.put(custom.id(), 3L);

        assertThatThrownBy(() -> service.delete(custom.id()))
                .satisfies(e -> assertError(e, IdentityError.ROLE_IN_USE))
                .satisfies(e -> assertThat(((IdentityException) e).params())
                        .containsEntry("id", custom.id().value())
                        .containsEntry("userCount", 3L));
        assertThat(roles.deleted()).isEmpty();
    }

    @Test
    void aForeignKeyViolationOfTheDeleteIsRoleInUse() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("fk");
        roles.failDeletesWith(failure);
        users.countsAfterDeleteFailure = 1L;

        assertThatThrownBy(() -> service.delete(custom.id()))
                .satisfies(e -> assertError(e, IdentityError.ROLE_IN_USE))
                .satisfies(e -> assertThat(((IdentityException) e).params()).containsEntry("userCount", 1L))
                .hasCause(failure);
    }

    private static void assertError(Throwable thrown, IdentityError error) {
        assertThat(thrown).isInstanceOf(IdentityException.class);
        assertThat(((IdentityException) thrown).error()).isEqualTo(error);
    }

    private static final class FakeUserRepository implements UserRepositoryPort {

        private final Map<RoleId, Long> counts = new HashMap<>();
        private Long countsAfterDeleteFailure;
        private int countCalls;

        @Override
        public Optional<User> findById(UserId id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<User> findByUsername(Username username) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<User> findAll() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long count() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countEnabledWithRole(RoleId role) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countWithRole(RoleId role) {
            countCalls++;
            if (countsAfterDeleteFailure != null && countCalls > 1) {
                return countsAfterDeleteFailure;
            }
            return counts.getOrDefault(role, 0L);
        }

        @Override
        public Map<RoleId, Long> countUsersByRole() {
            return Map.copyOf(counts);
        }

        @Override
        public User save(User user) {
            throw new UnsupportedOperationException();
        }
    }
}
