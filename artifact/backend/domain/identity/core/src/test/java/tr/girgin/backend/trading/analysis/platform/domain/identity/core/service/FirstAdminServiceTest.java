package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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

    private final FakeUserRepository fakeUserRepository = new FakeUserRepository();
    private final FakeHasher hasher = new FakeHasher();
    private final CountingGenerator generator = new CountingGenerator();

    private FirstAdminService service(String username, String password) {
        return service(12, username, password);
    }

    private FirstAdminService service(int minLength, String username, String password) {
        return new FirstAdminService(
                fakeUserRepository,
                hasher,
                generator,
                new PasswordPolicyService(minLength, "password1234567\n"),
                new UserChangeService(),
                new UsernameService(),
                username,
                password);
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

        assertThat(fakeUserRepository.users).isEmpty();
        assertThat(fakeUserRepository.saved).isEmpty();
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains("Xy7-weak") || message.contains("Password1234567"));
    }

    @Test
    void aWeakConfiguredPasswordIsIgnoredWhenUsersExist() {
        fakeUserRepository.users.add(existing());

        assertThatCode(() -> service("admin", "Xy7-weak").createFirstAdminIfNoUser())
                .doesNotThrowAnyException();

        assertThat(fakeUserRepository.saved).isEmpty();
    }

    @Test
    void theGeneratorIsAskedForAtLeastTwentyFourOrTheMinimumLength() {
        service("admin", null).createFirstAdminIfNoUser();
        assertThat(generator.lastLength).isEqualTo(24);

        fakeUserRepository.users.clear();
        service(40, "admin", null).createFirstAdminIfNoUser();
        assertThat(generator.lastLength).isEqualTo(40);
    }

    @Test
    void configuredPasswordCreatesTheAdminWithoutTheChangeFlag() {
        service("admin", "configured-secret").createFirstAdminIfNoUser();

        assertThat(fakeUserRepository.users).singleElement().satisfies(user -> {
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
        assertThat(fakeUserRepository.users).singleElement().satisfies(user -> {
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

        assertThat(fakeUserRepository.users)
                .singleElement()
                .satisfies(user -> assertThat(user.username()).isEqualTo(new Username("root")));
    }

    @Test
    void anInvalidUsernameFailsWithInvalidUsername() {
        assertThatThrownBy(() -> service("not valid!", "configured-secret").createFirstAdminIfNoUser())
                .isInstanceOfSatisfying(
                        IdentityException.class, e -> assertThat(e.error()).isEqualTo(IdentityError.INVALID_USERNAME));
        assertThat(fakeUserRepository.users).isEmpty();
    }

    @Test
    void anExistingUserMeansNothingIsDone() {
        fakeUserRepository.users.add(existing());

        service("admin", "configured-secret").createFirstAdminIfNoUser();
        service("admin", null).createFirstAdminIfNoUser();

        assertThat(fakeUserRepository.saved).isEmpty();
        assertThat(fakeUserRepository.users).hasSize(1);
        assertThat(generator.calls).isZero();
    }

    @Test
    void callingTwiceOnAnEmptyStoreCreatesOneUser() {
        FirstAdminService firstAdminService = service("admin", null);

        firstAdminService.createFirstAdminIfNoUser();
        firstAdminService.createFirstAdminIfNoUser();

        assertThat(fakeUserRepository.users).hasSize(1);
        assertThat(generator.calls).isEqualTo(1);
    }

    @Test
    void aDuplicateKeyOnSaveIsNotAnError() {
        fakeUserRepository.failWithDuplicate = true;

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

        assertThat(fakeUserRepository.users).isEmpty();
        assertThat(generator.last).isNotNull();
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains(generator.last));
    }

    @Test
    void theStartHookCreatesTheAdmin() {
        service("admin", "configured-secret").onStart();

        assertThat(fakeUserRepository.users).hasSize(1);
    }

    private static User existing() {
        return new User(
                new UserId(UUID.randomUUID().toString()),
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

    private static final class CountingGenerator extends OneTimePasswordService {

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
        public long countWithRole(RoleId role) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<RoleId, Long> countUsersByRole() {
            throw new UnsupportedOperationException();
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
