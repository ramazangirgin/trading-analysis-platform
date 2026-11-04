package tr.girgin.backend.trading.analysis.platform.orchestration.analysisview.model;

import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;

/** The owner of an analysis; {@code username} is null when the user no longer exists. */
public record AnalysisOwner(OwnerId id, Username username) {}
