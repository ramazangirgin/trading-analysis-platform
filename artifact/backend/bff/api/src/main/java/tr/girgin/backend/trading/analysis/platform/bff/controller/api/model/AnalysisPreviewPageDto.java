package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import java.util.List;

public record AnalysisPreviewPageDto(List<AnalysisPreviewSummaryDto> items, boolean hasMore) {}
