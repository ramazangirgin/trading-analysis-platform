package tr.girgin.backend.trading.analysis.platform.orchestration.ownership.inbound;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.AssignUnownedAnalysesPort;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.UserDirectoryPort;

/** analysis does not know users; identity knows who the first admin is. */
@Service
public class AssignAnalysisOwnersUseCase {

    private static final Logger log = LoggerFactory.getLogger(AssignAnalysisOwnersUseCase.class);

    private final UserDirectoryPort userDirectoryPort;
    private final AssignUnownedAnalysesPort assignUnownedAnalysesPort;

    AssignAnalysisOwnersUseCase(
            UserDirectoryPort userDirectoryPort, AssignUnownedAnalysesPort assignUnownedAnalysesPort) {
        this.userDirectoryPort = userDirectoryPort;
        this.assignUnownedAnalysesPort = assignUnownedAnalysesPort;
    }

    /**
     * Gives every analysis without an owner to the first admin; does nothing while there is none. After every
     * ContextRefreshedEvent listener the first admin exists; the startup import may still be running.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void assignUnownedToFirstAdmin() {
        userDirectoryPort.firstAdmin().ifPresent(admin -> {
            try {
                int assigned = assignUnownedAnalysesPort.assignUnowned(new OwnerId(admin.value()));
                if (assigned > 0) {
                    log.info("Gave {} analyses without an owner to the first admin", assigned);
                }
            } catch (OptimisticLockingFailureException e) {
                log.warn(
                        "The startup import changed analyses meanwhile; the next import or start gives them an owner",
                        e);
            }
        });
    }
}
