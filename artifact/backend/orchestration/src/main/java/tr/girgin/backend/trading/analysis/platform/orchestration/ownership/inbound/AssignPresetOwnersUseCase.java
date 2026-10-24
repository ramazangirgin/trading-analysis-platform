package tr.girgin.backend.trading.analysis.platform.orchestration.ownership.inbound;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.UserDirectoryUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.inbound.AssignUnownedPresetsUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.OwnerId;

/** settings does not know users; identity knows who the first admin is. */
@Service
public class AssignPresetOwnersUseCase {

    private static final Logger log = LoggerFactory.getLogger(AssignPresetOwnersUseCase.class);

    private final UserDirectoryUseCase userDirectory;
    private final AssignUnownedPresetsUseCase assignUnowned;

    AssignPresetOwnersUseCase(UserDirectoryUseCase userDirectory, AssignUnownedPresetsUseCase assignUnowned) {
        this.userDirectory = userDirectory;
        this.assignUnowned = assignUnowned;
    }

    /** Gives every preset without an owner to the first admin; does nothing while there is none. */
    @EventListener(ApplicationReadyEvent.class)
    public void assignUnownedToFirstAdmin() {
        userDirectory.firstAdmin().ifPresent(admin -> {
            try {
                int assigned = assignUnowned.assignUnowned(new OwnerId(admin.value()));
                if (assigned > 0) {
                    log.info("Gave {} presets without an owner to the first admin", assigned);
                }
            } catch (OptimisticLockingFailureException e) {
                log.warn("A preset changed meanwhile; the next start gives the presets without an owner an owner", e);
            }
        });
    }
}
