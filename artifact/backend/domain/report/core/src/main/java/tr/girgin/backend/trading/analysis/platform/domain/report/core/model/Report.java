package tr.girgin.backend.trading.analysis.platform.domain.report.core.model;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
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
        Instant modifiedAt) {

    public Report {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(modifiedAt, "modifiedAt");
        sections = Collections.unmodifiableMap(copy(sections, ReportSection.class));
        debates = Collections.unmodifiableMap(copy(debates, DebateSpeaker.class));
        sources = Set.copyOf(sources);
    }

    public boolean hasDecision() {
        return sections.containsKey(ReportSection.FINAL_TRADE_DECISION);
    }

    private static <K extends Enum<K>> EnumMap<K, String> copy(Map<K, String> source, Class<K> type) {
        EnumMap<K, String> copy = new EnumMap<>(type);
        if (source != null) {
            copy.putAll(source);
        }
        return copy;
    }
}
