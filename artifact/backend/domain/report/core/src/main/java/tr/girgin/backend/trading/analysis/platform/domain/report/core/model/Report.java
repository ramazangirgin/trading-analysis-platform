package tr.girgin.backend.trading.analysis.platform.domain.report.core.model;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Everything the data dir holds for one ticker and date, merged across its sources. Missing
 * parts are simply absent: a run that crashed half way still has its analyst sections.
 */
public record Report(
        ReportKey key,
        Map<ReportSection, String> sections,
        Map<DebateSpeaker, String> debates,
        Rating rating,
        Set<ReportSource> sources,
        Instant modifiedAt) {}
