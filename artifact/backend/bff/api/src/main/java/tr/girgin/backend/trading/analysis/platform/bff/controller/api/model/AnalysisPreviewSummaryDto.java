package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import java.time.LocalDate;

/** A card of the visitor list; {@code excerpt} is absent when no allowed section has text. */
public record AnalysisPreviewSummaryDto(
        String id, String ticker, LocalDate analysisDate, RatingEnumDto rating, String excerpt) {}
