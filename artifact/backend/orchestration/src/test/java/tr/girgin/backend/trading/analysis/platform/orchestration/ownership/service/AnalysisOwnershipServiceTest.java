package tr.girgin.backend.trading.analysis.platform.orchestration.ownership.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.AssignUnownedAnalysesUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.UserDirectoryUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;

class AnalysisOwnershipServiceTest {

    private final List<OwnerId> assignedTo = new ArrayList<>();

    @Test
    void givesTheAnalysesWithoutAnOwnerToTheFirstAdmin() {
        AnalysisOwnershipService service = service(Optional.of(new UserId("admin-1")));

        service.assignUnownedToFirstAdmin();

        assertThat(assignedTo).containsExactly(new OwnerId("admin-1"));
    }

    @Test
    void doesNothingBeforeThereIsAFirstAdmin() {
        AnalysisOwnershipService service = service(Optional.empty());

        service.assignUnownedToFirstAdmin();

        assertThat(assignedTo).isEmpty();
    }

    @Test
    void doesNotFailTheStartWhenTheImportChangesTheAnalysesAtTheSameTime() {
        AnalysisOwnershipService service = service(Optional.of(new UserId("admin-1")), _ -> {
            throw new OptimisticLockingFailureException("stale version");
        });

        assertThatCode(service::assignUnownedToFirstAdmin).doesNotThrowAnyException();
    }

    private AnalysisOwnershipService service(Optional<UserId> firstAdmin) {
        return service(firstAdmin, owner -> {
            assignedTo.add(owner);
            return 3;
        });
    }

    private AnalysisOwnershipService service(Optional<UserId> firstAdmin, AssignUnownedAnalysesUseCase assignUnowned) {
        return new AnalysisOwnershipService(
                new UserDirectoryUseCase() {
                    @Override
                    public Optional<UserId> firstAdmin() {
                        return firstAdmin;
                    }

                    @Override
                    public Map<UserId, Username> usernames(Collection<UserId> ids) {
                        return Map.of();
                    }
                },
                assignUnowned);
    }
}
