package tr.girgin.backend.trading.analysis.platform.orchestration.preview.model;

import java.time.LocalDate;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Rating;

/** A card of the visitor list; {@code excerpt} is {@code null} when no allowed section has text. */
public record AnalysisPreviewSummary(
        AnalysisId id, String ticker, LocalDate analysisDate, Rating rating, String excerpt) {}
