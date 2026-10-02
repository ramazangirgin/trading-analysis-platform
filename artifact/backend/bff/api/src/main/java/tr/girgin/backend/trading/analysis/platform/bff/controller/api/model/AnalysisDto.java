package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import java.time.Instant;

public record AnalysisDto(
        String id,
        AnalysisSpecDto spec,
        AnalysisStatusDto status,
        AnalysisSourceDto source,
        RatingDto rating,
        String decision,
        RunStatsDto stats,
        Instant createdAt,
        Instant startedAt,
        Instant endedAt,
        String errorCode,
        String errorMessage) {}
