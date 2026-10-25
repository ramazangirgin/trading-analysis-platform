package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * What a visitor reads of a partial analysis. {@code sections} is keyed like the report's ("market_report");
 * {@code lockedSections} holds the keys of the report sections and debate speakers that are not sent.
 */
public record AnalysisPreviewDto(
        String id,
        String ticker,
        LocalDate analysisDate,
        RatingEnumDto rating,
        Map<String, String> sections,
        List<String> lockedSections) {}
