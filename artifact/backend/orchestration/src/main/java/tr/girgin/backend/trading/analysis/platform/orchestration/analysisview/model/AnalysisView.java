package tr.girgin.backend.trading.analysis.platform.orchestration.analysisview.model;

import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisRights;

/** An analysis with the caller's rights; {@code owner} is null when the analysis has none. */
public record AnalysisView(Analysis analysis, AnalysisOwner owner, AnalysisRights rights) {}
