package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.PasswordHash;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;

class UserDirectoryServiceTest {

    @Test
    void theFirstAdminIsTheEarliestEnabledAdmin() {
        User later = user("later", "2026-10-03T10:00:00Z", true, RoleId.ADMIN);
        User earliest = user("earliest", "2026-10-02T10:00:00Z", true, RoleId.ADMIN);
        User analyst = user("analyst", "2026-10-01T10:00:00Z", true, RoleId.ANALYST);
        User disabled = user("disabled", "2026-10-01T09:00:00Z", false, RoleId.ADMIN);

        UserDirectoryService service = service(later, earliest, analyst, disabled);

        assertThat(service.firstAdmin()).contains(earliest.id());
    }

    @Test
    void theFirstAdminIsEmptyWithoutAnEnabledAdmin() {
        UserDirectoryService service = service(
                user("analyst", "2026-10-01T10:00:00Z", true, RoleId.ANALYST),
                user("disabled", "2026-10-01T10:00:00Z", false, RoleId.ADMIN));

        assertThat(service.firstAdmin()).isEmpty();
        assertThat(service().firstAdmin()).isEmpty();
    }

    @Test
    void usernamesLeaveOutUnknownIds() {
        User alice = user("alice", "2026-10-01T10:00:00Z", true, RoleId.ANALYST);
        User bob = user("bob", "2026-10-01T10:00:00Z", true, RoleId.ANALYST);
        UserDirectoryService service = service(alice, bob);

        assertThat(service.usernames(List.of(alice.id(), UserId.newId())))
                .containsExactly(Map.entry(alice.id(), new Username("alice")));
        assertThat(service.usernames(List.of())).isEmpty();
    }

    private static UserDirectoryService service(User... users) {
        return new UserDirectoryService(new FakeUsers(List.of(users)));
    }

    private static User user(String name, String createdAt, boolean enabled, RoleId... roleIds) {
        return new User(
                UserId.newId(),
                new Username(name),
                new PasswordHash("hash"),
                enabled,
                false,
                0,
                Optional.empty(),
                Optional.empty(),
                Instant.parse(createdAt),
                Instant.parse(createdAt),
                Set.of(roleIds),
                1L);
    }

    private record FakeUsers(List<User> users) implements UserRepositoryPort {

        @Override
        public Optional<User> findById(UserId id) {
            return users.stream().filter(u -> u.id().equals(id)).findFirst();
        }

        @Override
        public Optional<User> findByUsername(Username username) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<User> findAll() {
            return users;
        }

        @Override
        public long count() {
            return users.size();
        }

        @Override
        public long countEnabledWithRole(RoleId role) {
            throw new UnsupportedOperationException();
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
            throw new UnsupportedOperationException();
        }
    }
}
