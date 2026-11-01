package tr.girgin.backend.trading.analysis.platform.domain.settings.core.model;

/** Who asks and whether they may see the presets of every user. */
public record PresetAccess(OwnerId user, boolean allUsers) {}
