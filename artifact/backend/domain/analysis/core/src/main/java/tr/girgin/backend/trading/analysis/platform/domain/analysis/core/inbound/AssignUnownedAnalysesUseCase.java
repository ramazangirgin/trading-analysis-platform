package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound;

import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;

/** Gives the analyses that have no owner to one. */
public interface AssignUnownedAnalysesUseCase {

    /** Returns how many analyses got {@code owner}. */
    int assignUnowned(OwnerId owner);
}
