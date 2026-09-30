package tr.girgin.backend.trading.analysis.platform.orchestration.report;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
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
import tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound.WatchDataDirUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.Report;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportSection;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.RunHistoryEntry;

/**
 * report finds runs in the data dir (report files and the run history); analysis records them as
 * EXTERNAL analyses. Runs at startup, on every settled data dir change and on rescan.
 */
@Service
class ImportExistingRunsService implements ImportExistingRunsUseCase, DisposableBean {

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
    private final WatchDataDirUseCase watchDataDir;
    private final boolean importOnStartup;
    private final boolean watch;
    private final Duration settleTime;
    private final Clock clock;
    private final ScheduledExecutorService followUps = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("import-follow-up").factory());
    private AutoCloseable watchHandle;
    private ScheduledFuture<?> followUp;

    @Autowired
    ImportExistingRunsService(ScanReportsUseCase scanReports,
                              ScanRunHistoryUseCase scanHistory,
                              RegisterExternalAnalysisUseCase registerExternal,
                              WatchDataDirUseCase watchDataDir,
                              @Value("${platform.import.on-startup:true}") boolean importOnStartup,
                              @Value("${platform.import.watch.enabled:true}") boolean watch,
                              @Value("${platform.import.settle-time:10m}") Duration settleTime) {
        this(scanReports, scanHistory, registerExternal, watchDataDir, importOnStartup, watch, settleTime,
                Clock.systemUTC());
    }

    ImportExistingRunsService(ScanReportsUseCase scanReports,
                              ScanRunHistoryUseCase scanHistory,
                              RegisterExternalAnalysisUseCase registerExternal,
                              WatchDataDirUseCase watchDataDir,
                              boolean importOnStartup,
                              boolean watch,
                              Duration settleTime,
                              Clock clock) {
        this.scanReports = scanReports;
        this.scanHistory = scanHistory;
        this.registerExternal = registerExternal;
        this.watchDataDir = watchDataDir;
        this.importOnStartup = importOnStartup;
        this.watch = watch;
        this.settleTime = settleTime;
        this.clock = clock;
    }

    /**
     * In the background, so a large data dir does not hold up startup. Then the data dir is
     * watched, so runs made with the CLI or another UI show up without a rescan.
     */
    @EventListener(ContextRefreshedEvent.class)
    void importOnStartup() {
        if (importOnStartup) {
            Thread.ofVirtual().name("import-existing-runs").start(() -> {
                importQuietly();
                if (watch) {
                    startWatching();
                }
            });
        }
    }

    private synchronized void startWatching() {
        if (watchHandle == null) {
            try {
                watchHandle = watchDataDir.watch(this::importQuietly);
            } catch (RuntimeException e) {
                log.warn("Cannot watch the data dir; new runs show up after a rescan: {}", e.getMessage());
            }
        }
    }

    @Override
    public synchronized void destroy() throws Exception {
        followUps.shutdownNow();
        if (watchHandle != null) {
            watchHandle.close();
            watchHandle = null;
        }
    }

    private void importQuietly() {
        try {
            importExistingRuns();
        } catch (RuntimeException e) {
            log.error("Importing existing runs failed", e);
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
        Instant settledBefore = clock.instant().minus(settleTime);
        Instant lastUnsettled = null;
        for (Report report : scanReports.scan()) {
            if (!report.hasDecision() && report.modifiedAt().isAfter(settledBefore)) {
                // Likely a run still being written: importing it now would show it as failed.
                lastUnsettled = max(lastUnsettled, report.modifiedAt());
                continue;
            }
            externals.add(toExternal(report, latestCompleted.get(report.key())));
        }
        if (lastUnsettled != null) {
            scheduleFollowUp(lastUnsettled.plus(settleTime));
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

    /** Looks again once reports held back as possibly running have settled, even if nothing changes. */
    private void scheduleFollowUp(Instant at) {
        if (!watch) {
            return;
        }
        if (followUp != null && !followUp.isDone()) {
            followUp.cancel(false);
        }
        long delayMillis = Math.max(0, Duration.between(clock.instant(), at).toMillis()) + 1_000;
        try {
            followUp = followUps.schedule(this::importQuietly, delayMillis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // Shutting down.
        }
    }

    private static Instant max(Instant a, Instant b) {
        return a == null || b.isAfter(a) ? b : a;
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
