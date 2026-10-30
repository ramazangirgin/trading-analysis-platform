package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.CreateFirstAdminUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.password.PasswordHasherPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;

/**
 * Creates the first admin at startup, synchronously: the admin exists before the start completes, and an
 * invalid configuration fails the start.
 */
@Service
class FirstAdminService implements CreateFirstAdminUseCase {

    private static final Logger log = LoggerFactory.getLogger(FirstAdminService.class);

    private final UserRepositoryPort users;
    private final PasswordHasherPort hasher;
    private final OneTimePasswordService generator;
    private final PasswordPolicyService policy;
    private final UserChangeService userChanges;
    private final UsernameService usernames;
    private final String username;
    private final String password;

    @Autowired
    FirstAdminService(
            UserRepositoryPort users,
            PasswordHasherPort hasher,
            PasswordPolicyService policy,
            UserChangeService userChanges,
            UsernameService usernames,
            @Value("${platform.admin.username:admin}") String username,
            @Value("${platform.admin.password:}") String password) {
        this(users, hasher, new OneTimePasswordService(), policy, userChanges, usernames, username, password);
    }

    @SuppressWarnings("checkstyle:ParameterNumber") // constructor injection of the collaborators above
    FirstAdminService(
            UserRepositoryPort users,
            PasswordHasherPort hasher,
            OneTimePasswordService generator,
            PasswordPolicyService policy,
            UserChangeService userChanges,
            UsernameService usernames,
            String username,
            String password) {
        this.users = users;
        this.hasher = hasher;
        this.generator = generator;
        this.policy = policy;
        this.userChanges = userChanges;
        this.usernames = usernames;
        this.username = username;
        this.password = password;
    }

    @EventListener(ContextRefreshedEvent.class)
    void onStart() {
        createFirstAdminIfNoUser();
    }

    @Override
    public void createFirstAdminIfNoUser() {
        boolean configured = password != null && !password.isBlank();
        if (users.count() > 0) {
            if (configured) {
                log.info("Users exist: PLATFORM_ADMIN_USERNAME and PLATFORM_ADMIN_PASSWORD are ignored");
            }
            return;
        }
        Username name = usernames.username(username);
        if (configured) {
            checkConfiguredPassword(name);
        }
        String plain = configured
                ? password
                : generator.generate(Math.max(OneTimePasswordService.DEFAULT_LENGTH, policy.minLength()));
        User admin = new User(
                userChanges.newId(),
                name,
                hasher.hash(plain),
                true,
                !configured,
                0,
                Optional.empty(),
                Optional.empty(),
                null,
                null,
                Set.of(RoleId.ADMIN),
                null);
        try {
            users.save(admin);
        } catch (DuplicateKeyException e) {
            log.info("Another instance created the first admin at the same time", e);
            return;
        }
        if (configured) {
            log.info("First start: created the admin user \"{}\"", name.value());
        } else {
            log.warn(
                    "First start: created the admin user \"{}\" with the generated password {}. "
                            + "It is shown only this once; change it after the first login.",
                    name.value(),
                    plain);
        }
    }

    private void checkConfiguredPassword(Username name) {
        try {
            policy.check(password, name);
        } catch (IdentityException e) {
            throw new IllegalStateException("PLATFORM_ADMIN_PASSWORD breaks the password policy: "
                    + e.params().get("reasons")
                    + " (at least " + e.params().get("minLength") + " and at most "
                    + e.params().get("maxLength") + " characters, not a common password, not the username)."
                    + " Set another one, or leave it empty to get a generated password.");
        }
    }
}
