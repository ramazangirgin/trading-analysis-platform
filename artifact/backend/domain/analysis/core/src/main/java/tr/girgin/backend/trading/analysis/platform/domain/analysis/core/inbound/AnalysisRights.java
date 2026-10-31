package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound;

import java.util.List;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Visibility;

/** What a caller may do with one analysis; {@code allowedVisibilities} is in enum order. */
public record AnalysisRights(boolean canManage, List<Visibility> allowedVisibilities) {}
