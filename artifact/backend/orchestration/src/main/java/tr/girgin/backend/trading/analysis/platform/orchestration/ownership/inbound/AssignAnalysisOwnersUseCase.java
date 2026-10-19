package tr.girgin.backend.trading.analysis.platform.orchestration.ownership.inbound;

public interface AssignAnalysisOwnersUseCase {

    /** Gives every analysis without an owner to the first admin; does nothing while there is none. */
    void assignUnownedToFirstAdmin();
}
