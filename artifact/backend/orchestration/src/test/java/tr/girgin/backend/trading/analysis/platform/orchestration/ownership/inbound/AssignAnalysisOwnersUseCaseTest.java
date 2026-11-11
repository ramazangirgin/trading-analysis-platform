package tr.girgin.backend.trading.analysis.platform.orchestration.ownership.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.AssignUnownedAnalysesPort;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.UserDirectoryPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;

class AssignAnalysisOwnersUseCaseTest {

    private final List<OwnerId> assignedTo = new ArrayList<>();

    @Test
    void givesTheAnalysesWithoutAnOwnerToTheFirstAdmin() {
        AssignAnalysisOwnersUseCase assignAnalysisOwnersUseCase = service(Optional.of(new UserId("admin-1")));

        assignAnalysisOwnersUseCase.assignUnownedToFirstAdmin();

        assertThat(assignedTo).containsExactly(new OwnerId("admin-1"));
    }

    @Test
    void doesNothingBeforeThereIsAFirstAdmin() {
        AssignAnalysisOwnersUseCase assignAnalysisOwnersUseCase = service(Optional.empty());

        assignAnalysisOwnersUseCase.assignUnownedToFirstAdmin();

        assertThat(assignedTo).isEmpty();
    }

    @Test
    void doesNotFailTheStartWhenTheImportChangesTheAnalysesAtTheSameTime() {
        AssignAnalysisOwnersUseCase assignAnalysisOwnersUseCase = service(Optional.of(new UserId("admin-1")), _ -> {
            throw new OptimisticLockingFailureException("stale version");
        });

        assertThatCode(assignAnalysisOwnersUseCase::assignUnownedToFirstAdmin).doesNotThrowAnyException();
    }

    private AssignAnalysisOwnersUseCase service(Optional<UserId> firstAdmin) {
        return service(firstAdmin, owner -> {
            assignedTo.add(owner);
            return 3;
        });
    }

    private AssignAnalysisOwnersUseCase service(
            Optional<UserId> firstAdmin, AssignUnownedAnalysesPort assignUnownedAnalysesPort) {
        return new AssignAnalysisOwnersUseCase(
                new UserDirectoryPort() {
                    @Override
                    public Optional<UserId> firstAdmin() {
                        return firstAdmin;
                    }

                    @Override
                    public Map<UserId, Username> usernames(Collection<UserId> ids) {
                        return Map.of();
                    }
                },
                assignUnownedAnalysesPort);
    }
}
