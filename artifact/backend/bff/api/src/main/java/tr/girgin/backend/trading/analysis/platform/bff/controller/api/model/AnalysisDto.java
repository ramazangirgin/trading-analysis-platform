package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import java.time.Instant;
import java.util.List;

public record AnalysisDto(
        String id,
        AnalysisSpecDto spec,
        AnalysisStatusEnumDto status,
        AnalysisSourceEnumDto source,
        RatingEnumDto rating,
        String decision,
        RunStatsDto stats,
        Instant createdAt,
        Instant startedAt,
        Instant endedAt,
        String errorCode,
        String errorMessage,
        AnalysisOwnerDto owner,
        VisibilityEnumDto visibility,
        Instant visibilityChangedAt,
        Long version,
        boolean canManage,
        boolean canChangeVisibility,
        List<VisibilityEnumDto> allowedVisibilities) {}
