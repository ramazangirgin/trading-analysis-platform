package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

/** Who asks, whether they may see the analyses of every user, and whether they hold {@code analysis:share}. */
public record AnalysisAccess(OwnerId user, boolean allUsers, boolean share) {}
