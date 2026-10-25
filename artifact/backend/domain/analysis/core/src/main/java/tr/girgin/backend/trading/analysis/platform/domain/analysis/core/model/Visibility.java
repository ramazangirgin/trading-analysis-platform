package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

/** Who besides the owner may read an analysis, and how much of it a visitor without a session sees. */
public enum Visibility {
    PRIVATE,
    PUBLIC,
    PARTIAL;

    /** Whether every logged-in user reads the analysis in full. */
    public boolean sharedWithUsers() {
        return this != PRIVATE;
    }
}
