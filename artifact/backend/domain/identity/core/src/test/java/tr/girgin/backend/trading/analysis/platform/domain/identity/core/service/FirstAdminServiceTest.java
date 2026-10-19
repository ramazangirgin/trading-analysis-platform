package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityError;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.PasswordHash;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.password.PasswordHasherPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;

class FirstAdminServiceTest {

    private final FakeUserRepository users = new FakeUserRepository();
    private final FakeHasher hasher = new FakeHasher();
    private final CountingGenerator generator = new CountingGenerator();

    private FirstAdminService service(String username, String password) {
        return service(12, username, password);
    }

    private FirstAdminService service(int minLength, String username, String password) {
        return new FirstAdminService(
                users, hasher, generator, new PasswordPolicy(minLength, "password1234567\n"), username, password);
    }

    @Test
    void aWeakConfiguredPasswordStopsTheStartWithoutLoggingIt() {
        Logger logger = (Logger) LoggerFactory.getLogger(FirstAdminService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatThrownBy(() -> service("admin", "Xy7-weak").createFirstAdminIfNoUser())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PLATFORM_ADMIN_PASSWORD")
                    .hasMessageContaining("too_short")
                    .hasMessageContaining("12")
                    .hasMessageContaining("128")
                    .hasMessageNotContaining("Xy7-weak");
            assertThatThrownBy(() -> service("administrator1", "Administrator1").createFirstAdminIfNoUser())
                    .hasMessageContaining("same_as_username");
            assertThatThrownBy(() -> service("admin", "Password1234567").createFirstAdminIfNoUser())
                    .hasMessageContaining("common");
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(users.users).isEmpty();
        assertThat(users.saved).isEmpty();
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains("Xy7-weak") || message.contains("Password1234567"));
    }

    @Test
    void aWeakConfiguredPasswordIsIgnoredWhenUsersExist() {
        users.users.add(existing());

        assertThatCode(() -> service("admin", "Xy7-weak").createFirstAdminIfNoUser())
                .doesNotThrowAnyException();

        assertThat(users.saved).isEmpty();
    }

    @Test
    void theGeneratorIsAskedForAtLeastTwentyFourOrTheMinimumLength() {
        service("admin", null).createFirstAdminIfNoUser();
        assertThat(generator.lastLength).isEqualTo(24);

        users.users.clear();
        service(40, "admin", null).createFirstAdminIfNoUser();
        assertThat(generator.lastLength).isEqualTo(40);
    }

    @Test
    void configuredPasswordCreatesTheAdminWithoutTheChangeFlag() {
        service("admin", "configured-secret").createFirstAdminIfNoUser();

        assertThat(users.users).singleElement().satisfies(user -> {
            assertThat(user.username()).isEqualTo(new Username("admin"));
            assertThat(user.enabled()).isTrue();
            assertThat(user.mustChangePassword()).isFalse();
            assertThat(user.roleIds()).containsExactly(RoleId.ADMIN);
            assertThat(hasher.matches("configured-secret", user.passwordHash())).isTrue();
            assertThat(user.passwordHash().value()).isNotEqualTo("configured-secret");
        });
        assertThat(generator.calls).isZero();
    }

    @Test
    void noPasswordGeneratesOneAndFlagsTheUser() {
        service("admin", null).createFirstAdminIfNoUser();

        assertGenerated();
    }

    @Test
    void blankPasswordGeneratesOneAndFlagsTheUser() {
        service("admin", "  ").createFirstAdminIfNoUser();

        assertGenerated();
    }

    private void assertGenerated() {
        assertThat(users.users).singleElement().satisfies(user -> {
            assertThat(user.mustChangePassword()).isTrue();
            assertThat(user.roleIds()).containsExactly(RoleId.ADMIN);
            assertThat(hasher.matches(generator.last, user.passwordHash())).isTrue();
            assertThat(user.passwordHash().value()).isNotEqualTo(generator.last);
        });
        assertThat(generator.calls).isEqualTo(1);
    }

    @Test
    void aCustomUsernameIsUsed() {
        service("root", "configured-secret").createFirstAdminIfNoUser();

        assertThat(users.users)
                .singleElement()
                .satisfies(user -> assertThat(user.username()).isEqualTo(new Username("root")));
    }

    @Test
    void anInvalidUsernameFailsWithInvalidUsername() {
        assertThatThrownBy(() -> service("not valid!", "configured-secret").createFirstAdminIfNoUser())
                .isInstanceOfSatisfying(
                        IdentityException.class, e -> assertThat(e.error()).isEqualTo(IdentityError.INVALID_USERNAME));
        assertThat(users.users).isEmpty();
    }

    @Test
    void anExistingUserMeansNothingIsDone() {
        users.users.add(existing());

        service("admin", "configured-secret").createFirstAdminIfNoUser();
        service("admin", null).createFirstAdminIfNoUser();

        assertThat(users.saved).isEmpty();
        assertThat(users.users).hasSize(1);
        assertThat(generator.calls).isZero();
    }

    @Test
    void callingTwiceOnAnEmptyStoreCreatesOneUser() {
        FirstAdminService service = service("admin", null);

        service.createFirstAdminIfNoUser();
        service.createFirstAdminIfNoUser();

        assertThat(users.users).hasSize(1);
        assertThat(generator.calls).isEqualTo(1);
    }

    @Test
    void aDuplicateKeyOnSaveIsNotAnError() {
        users.failWithDuplicate = true;

        Logger logger = (Logger) LoggerFactory.getLogger(FirstAdminService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatCode(() -> service("admin", null).createFirstAdminIfNoUser())
                    .doesNotThrowAnyException();
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(users.users).isEmpty();
        assertThat(generator.last).isNotNull();
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains(generator.last));
    }

    @Test
    void theStartHookCreatesTheAdmin() {
        service("admin", "configured-secret").onStart();

        assertThat(users.users).hasSize(1);
    }

    private static User existing() {
        return new User(
                UserId.newId(),
                new Username("someone"),
                new PasswordHash("hash:x"),
                true,
                false,
                0,
                Optional.empty(),
                Optional.empty(),
                null,
                null,
                Set.of(),
                0L);
    }

    private static final class CountingGenerator extends OneTimePasswordGenerator {

        private int calls;
        private String last;
        private int lastLength;

        @Override
        String generate(int length) {
            lastLength = length;
            calls++;
            last = "generated-password-" + calls;
            return last;
        }
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
        private boolean failWithDuplicate;

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
        public User save(User user) {
            if (failWithDuplicate) {
                throw new DuplicateKeyException("duplicate");
            }
            saved.add(user);
            users.add(user);
            return user;
        }
    }
}
