package tr.girgin.backend.trading.analysis.platform.domain.report.core.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound.GetReportUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound.ScanReportsUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.DebateSpeaker;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.Report;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportContent;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportSection;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportSource;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.outbound.datadir.DataDirPort;

/**
 * Merges a ticker/date's sources into one report. The full state log wins over the report tree:
 * it is written once, at the end, from the complete state, while report files may be partial.
 */
@Service
class ReportService implements ScanReportsUseCase, GetReportUseCase {

    private static final Comparator<ReportContent> PRIORITY =
            Comparator.comparing((ReportContent c) -> c.source() == ReportSource.FULL_STATE ? 1 : 0);

    private final DataDirPort dataDir;

    ReportService(DataDirPort dataDir) {
        this.dataDir = dataDir;
    }

    @Override
    public List<Report> scan() {
        Map<ReportKey, List<ReportContent>> byKey = dataDir.readAll().stream()
                .collect(Collectors.groupingBy(ReportContent::key, LinkedHashMap::new, Collectors.toList()));
        return byKey.values().stream()
                .map(ReportService::merge)
                .sorted(Comparator.comparing((Report r) -> r.key().tradeDate())
                        .reversed()
                        .thenComparing(r -> r.key().ticker()))
                .toList();
    }

    @Override
    public Optional<Report> get(ReportKey key) {
        List<ReportContent> contents = dataDir.read(key);
        return contents.isEmpty() ? Optional.empty() : Optional.of(merge(contents));
    }

    private static Report merge(List<ReportContent> contents) {
        Map<ReportSection, String> sections = new EnumMap<>(ReportSection.class);
        Map<DebateSpeaker, String> debates = new EnumMap<>(DebateSpeaker.class);
        EnumSet<ReportSource> sources = EnumSet.noneOf(ReportSource.class);
        Instant modified = Instant.EPOCH;
        for (ReportContent content : contents.stream().sorted(PRIORITY).toList()) {
            putNonBlank(sections, content.sections());
            putNonBlank(debates, content.debates());
            sources.add(content.source());
            if (content.modifiedAt().isAfter(modified)) {
                modified = content.modifiedAt();
            }
        }
        String decision = sections.get(ReportSection.FINAL_TRADE_DECISION);
        return new Report(
                contents.getFirst().key(),
                sections,
                debates,
                decision == null ? null : DecisionRatingParser.parse(decision),
                sources,
                modified);
    }

    private static <K> void putNonBlank(Map<K, String> target, Map<K, String> source) {
        source.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                target.put(key, value);
            }
        });
    }
}
