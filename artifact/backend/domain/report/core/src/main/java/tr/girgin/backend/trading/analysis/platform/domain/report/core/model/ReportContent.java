package tr.girgin.backend.trading.analysis.platform.domain.report.core.model;

import java.time.Instant;
import java.util.Map;

/** What one source file contributed; the service merges several into a {@link Report}. */
public record ReportContent(
        ReportKey key,
        ReportSource source,
        Map<ReportSection, String> sections,
        Map<DebateSpeaker, String> debates,
        Instant modifiedAt) {}
