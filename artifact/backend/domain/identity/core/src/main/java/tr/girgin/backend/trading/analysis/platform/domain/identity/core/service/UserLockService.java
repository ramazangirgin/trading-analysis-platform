package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityError;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.UnlockUserUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;

@Service
class UserLockService implements UnlockUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(UserLockService.class);
    private static final int MAX_ATTEMPTS = 3;

    private final UserRepositoryPort users;

    private final LoginLockoutService lockout;

    UserLockService(UserRepositoryPort users, LoginLockoutService lockout) {
        this.users = users;
        this.lockout = lockout;
    }

    @Override
    public void unlock(UserId id) {
        for (int attempt = 1; ; attempt++) {
            User user = users.findById(id)
                    .orElseThrow(() -> new IdentityException(
                            IdentityError.USER_NOT_FOUND,
                            "User " + id.value() + " not found",
                            Map.of("id", id.value())));
            if (user.failedLoginCount() == 0 && user.lockedUntil().isEmpty()) {
                return;
            }
            try {
                users.save(lockout.unlocked(user));
                log.info("User {} unlocked", id.value());
                return;
            } catch (OptimisticLockingFailureException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                log.debug("Unlock of user {} conflicted, trying again", id.value(), e);
            }
        }
    }
}
