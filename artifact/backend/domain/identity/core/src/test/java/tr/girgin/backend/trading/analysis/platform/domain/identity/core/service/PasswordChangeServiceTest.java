package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityError;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.AuthenticatedUser;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.PasswordHash;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.password.PasswordHasherPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.RoleRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;

class PasswordChangeServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

    private final FakeUserRepository users = new FakeUserRepository();
    private final FakeRoleRepository roles = new FakeRoleRepository();
    private final PasswordChangeService service = new PasswordChangeService(
            users, new FakeHasher(), new AuthenticatedUserService(roles), new PasswordPolicyService(8, "password\n"));

    @Test
    void aCorrectChangeStoresTheNewHashClearsTheFlagAndKeepsTheRest() {
        Role reader = new Role(RoleId.newId(), "reader", false, "reader", Set.of(Permission.ANALYSIS_READ), 1L);
        roles.roles.put(reader.id(), reader);
        User user = user("alice", true, true, reader.id());
        users.users.add(user);

        AuthenticatedUser result = service.changePassword(user.id(), "old-secret", "new-secret");

        assertThat(users.saved)
                .singleElement()
                .isEqualTo(user.withPassword(new PasswordHash("hash:new-secret"), false));
        assertThat(users.saved.getFirst().version()).isEqualTo(7L);
        assertThat(result.id()).isEqualTo(user.id());
        assertThat(result.roleNames()).containsExactly("reader");
        assertThat(result.permissions()).containsExactly(Permission.ANALYSIS_READ);
        assertThat(result.mustChangePassword()).isFalse();
    }

    @Test
    void aWrongCurrentPasswordSavesNothing() {
        User user = user("alice", true, true);
        users.users.add(user);

        assertThatThrownBy(() -> service.changePassword(user.id(), "wrong", "new-secret"))
                .isInstanceOfSatisfying(
                        IdentityException.class,
                        e -> assertThat(e.error()).isEqualTo(IdentityError.WRONG_CURRENT_PASSWORD));
        assertThat(users.saved).isEmpty();
    }

    @Test
    void aWeakNewPasswordIsRejectedAndSavesNothing() {
        User user = user("alice", true, true);
        users.users.add(user);

        for (String weak : List.of("short", "password", "alice")) {
            assertThatThrownBy(() -> service.changePassword(user.id(), "old-secret", weak))
                    .isInstanceOfSatisfying(
                            IdentityException.class, e -> assertThat(e.error()).isEqualTo(IdentityError.WEAK_PASSWORD));
        }
        assertThat(users.saved).isEmpty();
    }

    @Test
    void aWrongCurrentPasswordIsReportedBeforeAWeakNewOne() {
        User user = user("alice", true, true);
        users.users.add(user);

        assertThatThrownBy(() -> service.changePassword(user.id(), "wrong", "short"))
                .isInstanceOfSatisfying(
                        IdentityException.class,
                        e -> assertThat(e.error()).isEqualTo(IdentityError.WRONG_CURRENT_PASSWORD));
    }

    @Test
    void theSamePasswordIsRejected() {
        User user = user("alice", true, true);
        users.users.add(user);

        assertThatThrownBy(() -> service.changePassword(user.id(), "old-secret", "old-secret"))
                .isInstanceOfSatisfying(
                        IdentityException.class,
                        e -> assertThat(e.error()).isEqualTo(IdentityError.PASSWORD_UNCHANGED));
        assertThat(users.saved).isEmpty();
    }

    @Test
    void anUnknownOrDisabledUserFailsWithBadCredentials() {
        User disabled = user("bob", false, false);
        users.users.add(disabled);

        for (UserId id : List.of(UserId.newId(), disabled.id())) {
            assertThatThrownBy(() -> service.changePassword(id, "old-secret", "new-secret"))
                    .isInstanceOfSatisfying(
                            IdentityException.class,
                            e -> assertThat(e.error()).isEqualTo(IdentityError.BAD_CREDENTIALS));
        }
        assertThat(users.saved).isEmpty();
    }

    @Test
    void thePasswordsNeverAppearInALogLine() {
        User user = user("alice", true, false);
        users.users.add(user);
        Logger logger = (Logger) LoggerFactory.getLogger(PasswordChangeService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.changePassword(user.id(), "old-secret", "new-secret");
            assertThatThrownBy(() -> service.changePassword(user.id(), "wrong-secret", "other-secret"))
                    .isInstanceOf(IdentityException.class);
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).isNotEmpty();
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains("secret"));
    }

    private static User user(String name, boolean enabled, boolean mustChangePassword, RoleId... roleIds) {
        return new User(
                UserId.newId(),
                new Username(name),
                new PasswordHash("hash:old-secret"),
                enabled,
                mustChangePassword,
                2,
                Optional.of(NOW),
                Optional.empty(),
                NOW,
                NOW,
                Set.of(roleIds),
                7L);
    }

    private static final class FakeHasher implements PasswordHasherPort {

        @Override
        public PasswordHash hash(CharSequence rawPassword) {
            return new PasswordHash("hash:" + rawPassword);
        }

        @Override
        public boolean matches(CharSequence rawPassword, PasswordHash hash) {
            return hash.value().equals("hash:" + rawPassword);
        }

        @Override
        public boolean needsRehash(PasswordHash hash) {
            return false;
        }
    }

    private static final class FakeUserRepository implements UserRepositoryPort {

        private final List<User> users = new ArrayList<>();
        private final List<User> saved = new ArrayList<>();

        @Override
        public Optional<User> findById(UserId id) {
            return users.stream().filter(u -> u.id().equals(id)).findFirst();
        }

        @Override
        public Optional<User> findByUsername(Username username) {
            return users.stream().filter(u -> u.username().equals(username)).findFirst();
        }

        @Override
        public List<User> findAll() {
            return List.copyOf(users);
        }

        @Override
        public long count() {
            return users.size();
        }

        @Override
        public long countEnabledWithRole(RoleId role) {
            return users.stream()
                    .filter(u -> u.enabled() && u.roleIds().contains(role))
                    .count();
        }

        @Override
        public long countWithRole(RoleId role) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<RoleId, Long> countUsersByRole() {
            throw new UnsupportedOperationException();
        }

        @Override
        public User save(User user) {
            saved.add(user);
            users.removeIf(u -> u.id().equals(user.id()));
            users.add(user);
            return user;
        }
    }

    private static final class FakeRoleRepository implements RoleRepositoryPort {

        private final Map<RoleId, Role> roles = new HashMap<>();

        @Override
        public Optional<Role> findById(RoleId id) {
            return Optional.ofNullable(roles.get(id));
        }

        @Override
        public Optional<Role> findByName(String name) {
            return roles.values().stream().filter(r -> r.name().equals(name)).findFirst();
        }

        @Override
        public List<Role> findAll() {
            return List.copyOf(roles.values());
        }

        @Override
        public Role save(Role role) {
            roles.put(role.id(), role);
            return role;
        }

        @Override
        public void deleteById(RoleId id) {
            throw new UnsupportedOperationException();
        }
    }
}
