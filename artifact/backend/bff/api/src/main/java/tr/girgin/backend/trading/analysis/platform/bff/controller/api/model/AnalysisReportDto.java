package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The data dir report for an analysis. {@code sections} is keyed like the runner's report_section
 * events ("market_report"); {@code debates} by speaker ("bull", "research_judge", "risk_judge").
 */
public record AnalysisReportDto(
        Map<String, String> sections,
        Map<String, String> debates,
        RatingDto rating,
        List<String> sources,
        Instant modifiedAt) {}
