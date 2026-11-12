package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

import java.util.List;

/** What a caller may do with one analysis; {@code allowedVisibilities} is in enum order. */
public record AnalysisRights(boolean canManage, boolean canChangeVisibility, List<Visibility> allowedVisibilities) {}
