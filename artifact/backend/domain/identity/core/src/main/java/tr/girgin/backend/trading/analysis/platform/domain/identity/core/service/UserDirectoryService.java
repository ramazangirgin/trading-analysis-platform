package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.UserDirectoryUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;

@Service
class UserDirectoryService implements UserDirectoryUseCase {

    private final UserRepositoryPort users;

    UserDirectoryService(UserRepositoryPort users) {
        this.users = users;
    }

    @Override
    public Optional<UserId> firstAdmin() {
        return users.findAll().stream()
                .filter(user -> user.enabled() && user.roleIds().contains(RoleId.ADMIN))
                .min(Comparator.comparing(User::createdAt)
                        .thenComparing(user -> user.id().value()))
                .map(User::id);
    }

    @Override
    public Map<UserId, Username> usernames(Collection<UserId> ids) {
        Set<UserId> wanted = new HashSet<>(ids);
        return users.findAll().stream()
                .filter(user -> wanted.contains(user.id()))
                .collect(Collectors.toMap(User::id, User::username));
    }
}
