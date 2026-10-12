package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
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
        return new FirstAdminService(users, hasher, generator, username, password);
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

        assertThatCode(() -> service("admin", null).createFirstAdminIfNoUser()).doesNotThrowAnyException();
        assertThat(users.users).isEmpty();
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
                null,
                null,
                Set.of(),
                0L);
    }

    private static final class CountingGenerator extends OneTimePasswordGenerator {

        private int calls;
        private String last;

        @Override
        String generate() {
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
