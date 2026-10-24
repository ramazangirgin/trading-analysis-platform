package tr.girgin.backend.trading.analysis.platform.domain.settings.core.inbound;

import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.OwnerId;

/** Gives the presets that existed before ownership to a user. */
public interface AssignUnownedPresetsUseCase {

    /** Returns how many presets were given to {@code owner}; presets that have an owner stay as they are. */
    int assignUnowned(OwnerId owner);
}
