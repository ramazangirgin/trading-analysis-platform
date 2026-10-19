package tr.girgin.backend.trading.analysis.platform.orchestration.ownership.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.AssignUnownedAnalysesUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.UserDirectoryUseCase;
import tr.girgin.backend.trading.analysis.platform.orchestration.ownership.inbound.AssignAnalysisOwnersUseCase;

/** analysis does not know users; identity knows who the first admin is. */
@Service
class AnalysisOwnershipService implements AssignAnalysisOwnersUseCase {

    private static final Logger log = LoggerFactory.getLogger(AnalysisOwnershipService.class);

    private final UserDirectoryUseCase userDirectory;
    private final AssignUnownedAnalysesUseCase assignUnowned;

    AnalysisOwnershipService(UserDirectoryUseCase userDirectory, AssignUnownedAnalysesUseCase assignUnowned) {
        this.userDirectory = userDirectory;
        this.assignUnowned = assignUnowned;
    }

    /** After every ContextRefreshedEvent listener the first admin exists; the startup import may still be running. */
    @EventListener(ApplicationReadyEvent.class)
    @Override
    public void assignUnownedToFirstAdmin() {
        userDirectory.firstAdmin().ifPresent(admin -> {
            try {
                int assigned = assignUnowned.assignUnowned(new OwnerId(admin.value()));
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
