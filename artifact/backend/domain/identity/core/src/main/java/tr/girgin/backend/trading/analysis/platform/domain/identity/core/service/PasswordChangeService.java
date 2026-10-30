package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityError;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.ChangePasswordUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.AuthenticatedUser;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.password.PasswordHasherPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;

@Service
class PasswordChangeService implements ChangePasswordUseCase {

    private static final Logger log = LoggerFactory.getLogger(PasswordChangeService.class);

    private final UserRepositoryPort users;
    private final PasswordHasherPort hasher;
    private final AuthenticatedUserService authenticatedUsers;
    private final PasswordPolicyService policy;
    private final UserChangeService userChanges;

    PasswordChangeService(
            UserRepositoryPort users,
            PasswordHasherPort hasher,
            AuthenticatedUserService authenticatedUsers,
            PasswordPolicyService policy,
            UserChangeService userChanges) {
        this.users = users;
        this.hasher = hasher;
        this.authenticatedUsers = authenticatedUsers;
        this.policy = policy;
        this.userChanges = userChanges;
    }

    @Override
    public AuthenticatedUser changePassword(UserId id, CharSequence currentPassword, CharSequence newPassword) {
        User user = users.findById(id)
                .filter(User::enabled)
                .orElseThrow(() -> new IdentityException(IdentityError.BAD_CREDENTIALS, "Bad credentials", Map.of()));
        if (!hasher.matches(currentPassword, user.passwordHash())) {
            throw new IdentityException(
                    IdentityError.WRONG_CURRENT_PASSWORD, "The current password is wrong", Map.of());
        }
        policy.check(newPassword, user.username());
        if (hasher.matches(newPassword, user.passwordHash())) {
            throw new IdentityException(
                    IdentityError.PASSWORD_UNCHANGED, "The new password is the same as the current one", Map.of());
        }
        User saved = users.save(userChanges.withPassword(user, hasher.hash(newPassword), false));
        log.info("Password changed for user {}", saved.id().value());
        return authenticatedUsers.create(saved);
    }
}
