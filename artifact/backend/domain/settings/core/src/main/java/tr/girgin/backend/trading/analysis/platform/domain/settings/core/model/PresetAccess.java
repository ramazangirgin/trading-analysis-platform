package tr.girgin.backend.trading.analysis.platform.domain.settings.core.model;

import java.util.Objects;

/** Who asks and whether they may see the presets of every user. */
public record PresetAccess(OwnerId user, boolean allUsers) {

    public PresetAccess {
        Objects.requireNonNull(user, "user");
    }

    /** A preset without an owner is seen by {@code allUsers} only. */
    public boolean canSee(Preset preset) {
        return allUsers || user.equals(preset.owner());
    }
}
