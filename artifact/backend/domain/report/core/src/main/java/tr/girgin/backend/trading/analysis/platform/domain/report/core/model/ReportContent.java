package tr.girgin.backend.trading.analysis.platform.domain.report.core.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** What one source file contributed; the service merges several into a {@link Report}. */
public record ReportContent(
        ReportKey key,
        ReportSource source,
        Map<ReportSection, String> sections,
        Map<DebateSpeaker, String> debates,
        Instant modifiedAt) {

    public ReportContent {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(modifiedAt, "modifiedAt");
        sections = Map.copyOf(sections);
        debates = Map.copyOf(debates);
    }
}
