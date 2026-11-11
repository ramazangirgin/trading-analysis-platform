package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityError;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.ChangePasswordPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.AuthenticatedUser;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.password.PasswordHasherPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;

@Service
class PasswordChangeService implements ChangePasswordPort {

    private static final Logger log = LoggerFactory.getLogger(PasswordChangeService.class);

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordHasherPort passwordHasherPort;
    private final AuthenticatedUserService authenticatedUserService;
    private final PasswordPolicyService passwordPolicyService;
    private final UserChangeService userChangeService;

    PasswordChangeService(
            UserRepositoryPort userRepositoryPort,
            PasswordHasherPort passwordHasherPort,
            AuthenticatedUserService authenticatedUserService,
            PasswordPolicyService passwordPolicyService,
            UserChangeService userChangeService) {
        this.userRepositoryPort = userRepositoryPort;
        this.passwordHasherPort = passwordHasherPort;
        this.authenticatedUserService = authenticatedUserService;
        this.passwordPolicyService = passwordPolicyService;
        this.userChangeService = userChangeService;
    }

    @Override
    public AuthenticatedUser changePassword(UserId id, CharSequence currentPassword, CharSequence newPassword) {
        User user = userRepositoryPort
                .findById(id)
                .filter(User::enabled)
                .orElseThrow(() -> new IdentityException(IdentityError.BAD_CREDENTIALS, "Bad credentials", Map.of()));
        if (!passwordHasherPort.matches(currentPassword, user.passwordHash())) {
            throw new IdentityException(
                    IdentityError.WRONG_CURRENT_PASSWORD, "The current password is wrong", Map.of());
        }
        passwordPolicyService.check(newPassword, user.username());
        if (passwordHasherPort.matches(newPassword, user.passwordHash())) {
            throw new IdentityException(
                    IdentityError.PASSWORD_UNCHANGED, "The new password is the same as the current one", Map.of());
        }
        User saved = userRepositoryPort.save(
                userChangeService.withPassword(user, passwordHasherPort.hash(newPassword), false));
        log.info("Password changed for user {}", saved.id().value());
        return authenticatedUserService.create(saved);
    }
}
