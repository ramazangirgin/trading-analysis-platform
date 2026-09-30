package tr.girgin.backend.trading.analysis.platform.orchestration.report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.RegisterExternalAnalysisUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalAnalysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalRegistration;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalRun;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Rating;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunStats;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound.ScanReportsUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound.ScanRunHistoryUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.Report;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportSection;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.RunHistoryEntry;

/**
 * report finds runs in the data dir (report files and the run history); analysis records them as
 * EXTERNAL analyses.
 */
@Service
class ImportExistingRunsService implements ImportExistingRunsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ImportExistingRunsService.class);

    private static final Map<ReportSection, Analyst> ANALYST_SECTIONS = Map.of(
            ReportSection.MARKET_REPORT, Analyst.MARKET,
            ReportSection.SENTIMENT_REPORT, Analyst.SOCIAL,
            ReportSection.NEWS_REPORT, Analyst.NEWS,
            ReportSection.FUNDAMENTALS_REPORT, Analyst.FUNDAMENTALS);
    private static final Map<String, Analyst> ANALYST_KEYS = Map.of(
            "market", Analyst.MARKET,
            "social", Analyst.SOCIAL,
            "news", Analyst.NEWS,
            "fundamentals", Analyst.FUNDAMENTALS);

    private final ScanReportsUseCase scanReports;
    private final ScanRunHistoryUseCase scanHistory;
    private final RegisterExternalAnalysisUseCase registerExternal;
    private final boolean importOnStartup;

    ImportExistingRunsService(ScanReportsUseCase scanReports,
                              ScanRunHistoryUseCase scanHistory,
                              RegisterExternalAnalysisUseCase registerExternal,
                              @Value("${platform.import.on-startup:true}") boolean importOnStartup) {
        this.scanReports = scanReports;
        this.scanHistory = scanHistory;
        this.registerExternal = registerExternal;
        this.importOnStartup = importOnStartup;
    }

    /** In the background, so a large data dir does not hold up startup. */
    @EventListener(ContextRefreshedEvent.class)
    void importOnStartup() {
        if (importOnStartup) {
            Thread.ofVirtual().name("import-existing-runs").start(() -> {
                try {
                    importExistingRuns();
                } catch (RuntimeException e) {
                    log.error("Importing existing runs failed", e);
                }
            });
        }
    }

    @Override
    public synchronized ImportResult importExistingRuns() {
        List<RunHistoryEntry> history = scanHistory.scan();
        // Newest first: the first completed run of a ticker and date is the one its files are from.
        Map<ReportKey, RunHistoryEntry> latestCompleted = new HashMap<>();
        history.stream()
                .filter(entry -> entry.status() == RunHistoryEntry.Status.COMPLETED)
                .forEach(entry -> latestCompleted.putIfAbsent(entry.key(), entry));
        List<ExternalAnalysis> externals = new ArrayList<>();
        for (Report report : scanReports.scan()) {
            externals.add(toExternal(report, latestCompleted.get(report.key())));
        }
        // A completed run left report files, imported above; one that failed or was stopped left
        // little or nothing, so the history is all there is of it.
        history.stream()
                .filter(entry -> entry.status() != RunHistoryEntry.Status.COMPLETED)
                .map(entry -> ExternalAnalysis.runHistory(entry.key().ticker(), entry.key().tradeDate(),
                        analysts(entry), toRun(entry)))
                .forEach(externals::add);

        int created = 0;
        int updated = 0;
        int unchanged = 0;
        for (ExternalAnalysis external : externals) {
            ExternalRegistration registration = registerExternal.register(external);
            switch (registration.outcome()) {
                case CREATED -> created++;
                case UPDATED -> updated++;
                case UNCHANGED -> unchanged++;
            }
        }
        ImportResult result = new ImportResult(externals.size(), created, updated, unchanged);
        log.info("Imported existing runs: {}", result);
        return result;
    }

    private static ExternalAnalysis toExternal(Report report, RunHistoryEntry run) {
        List<Analyst> analysts = new ArrayList<>();
        ANALYST_SECTIONS.forEach((section, analyst) -> {
            if (report.sections().containsKey(section)) {
                analysts.add(analyst);
            }
        });
        if (analysts.isEmpty() && run != null) {
            analysts.addAll(analysts(run));
        }
        Rating rating = report.rating() == null ? null : Rating.valueOf(report.rating().name());
        return ExternalAnalysis.reportFiles(report.key().ticker(), report.key().tradeDate(), analysts, rating,
                report.sections().get(ReportSection.FINAL_TRADE_DECISION), run == null ? null : toRun(run),
                run == null ? report.modifiedAt() : run.endedAt());
    }

    private static ExternalRun toRun(RunHistoryEntry entry) {
        AnalysisStatus status = switch (entry.status()) {
            case COMPLETED -> AnalysisStatus.COMPLETED;
            case FAILED -> AnalysisStatus.FAILED;
            case STOPPED -> AnalysisStatus.STOPPED;
        };
        Integer depth = entry.researchDepth();
        return new ExternalRun(entry.id(), status, entry.error(),
                Objects.requireNonNullElse(entry.llmProvider(), Analysis.UNKNOWN),
                Objects.requireNonNullElse(entry.deepThinkLlm(), Analysis.UNKNOWN),
                Objects.requireNonNullElse(entry.quickThinkLlm(), Analysis.UNKNOWN),
                depth == null || depth < 1 ? 1 : depth,
                Objects.requireNonNullElse(entry.outputLanguage(), "English"),
                new RunStats(entry.llmCalls(), entry.toolCalls(), entry.tokensIn(), entry.tokensOut(),
                        entry.costUsd(), entry.elapsed()),
                entry.startedAt(), entry.endedAt());
    }

    private static List<Analyst> analysts(RunHistoryEntry entry) {
        return entry.analysts().stream()
                .map(key -> ANALYST_KEYS.get(key.toLowerCase(Locale.ROOT)))
                .filter(Objects::nonNull)
                .toList();
    }
}
