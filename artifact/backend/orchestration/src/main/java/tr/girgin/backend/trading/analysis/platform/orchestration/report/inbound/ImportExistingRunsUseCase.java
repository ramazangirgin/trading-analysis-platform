package tr.girgin.backend.trading.analysis.platform.orchestration.report.inbound;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
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
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.RegisterExternalAnalysisPort;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.ValidateExternalAnalysesPort;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalAnalysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalRegistration;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalRun;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Rating;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunStats;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.UserDirectoryPort;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound.CheckReportDecisionPort;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound.ScanReportsPort;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound.ScanRunHistoryPort;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound.WatchDataDirPort;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.Report;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportSection;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.RunHistoryEntry;
import tr.girgin.backend.trading.analysis.platform.orchestration.report.model.ImportResult;

/**
 * report finds runs in the data dir (report files and the run history); analysis records them as
 * EXTERNAL analyses. Runs at startup, on every settled data dir change and on rescan.
 */
@Service
public class ImportExistingRunsUseCase implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ImportExistingRunsUseCase.class);

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

    private final ScanReportsPort scanReportsPort;
    private final ScanRunHistoryPort scanRunHistoryPort;
    private final CheckReportDecisionPort checkReportDecisionPort;
    private final ValidateExternalAnalysesPort validateExternalAnalysesPort;
    private final RegisterExternalAnalysisPort registerExternalAnalysisPort;
    private final UserDirectoryPort userDirectoryPort;
    private final WatchDataDirPort watchDataDirPort;
    private final boolean importOnStartup;
    private final boolean watch;
    private final Duration settleTime;
    private final Clock clock;
    private final ScheduledExecutorService followUpScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor(
                    Thread.ofVirtual().name("import-follow-up").factory());
    private AutoCloseable watchHandle;
    private ScheduledFuture<?> followUp;

    @Autowired
    @SuppressWarnings("checkstyle:ParameterNumber") // the ports and the settings it is built from
    ImportExistingRunsUseCase(
            ScanReportsPort scanReportsPort,
            ScanRunHistoryPort scanRunHistoryPort,
            CheckReportDecisionPort checkReportDecisionPort,
            ValidateExternalAnalysesPort validateExternalAnalysesPort,
            RegisterExternalAnalysisPort registerExternalAnalysisPort,
            UserDirectoryPort userDirectoryPort,
            WatchDataDirPort watchDataDirPort,
            @Value("${platform.import.on-startup:true}") boolean importOnStartup,
            @Value("${platform.import.watch.enabled:true}") boolean watch,
            @Value("${platform.import.settle-minutes:10}") long settleMinutes) {
        this(
                scanReportsPort,
                scanRunHistoryPort,
                checkReportDecisionPort,
                validateExternalAnalysesPort,
                registerExternalAnalysisPort,
                userDirectoryPort,
                watchDataDirPort,
                importOnStartup,
                watch,
                Duration.ofMinutes(settleMinutes),
                Clock.systemUTC());
    }

    @SuppressWarnings("checkstyle:ParameterNumber") // the settings above, with the settle time and clock given
    ImportExistingRunsUseCase(
            ScanReportsPort scanReportsPort,
            ScanRunHistoryPort scanRunHistoryPort,
            CheckReportDecisionPort checkReportDecisionPort,
            ValidateExternalAnalysesPort validateExternalAnalysesPort,
            RegisterExternalAnalysisPort registerExternalAnalysisPort,
            UserDirectoryPort userDirectoryPort,
            WatchDataDirPort watchDataDirPort,
            boolean importOnStartup,
            boolean watch,
            Duration settleTime,
            Clock clock) {
        this.scanReportsPort = scanReportsPort;
        this.scanRunHistoryPort = scanRunHistoryPort;
        this.checkReportDecisionPort = checkReportDecisionPort;
        this.validateExternalAnalysesPort = validateExternalAnalysesPort;
        this.registerExternalAnalysisPort = registerExternalAnalysisPort;
        this.userDirectoryPort = userDirectoryPort;
        this.watchDataDirPort = watchDataDirPort;
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

    @SuppressWarnings("checkstyle:IllegalCatch") // watching is optional: rescans still work
    private synchronized void startWatching() {
        if (watchHandle == null) {
            try {
                watchHandle = watchDataDirPort.watch(this::importQuietly);
            } catch (RuntimeException e) {
                log.warn("Cannot watch the data dir; new runs show up after a rescan: {}", e.getMessage());
            }
        }
    }

    @Override
    public synchronized void destroy() throws Exception {
        followUpScheduledExecutorService.shutdownNow();
        if (watchHandle != null) {
            watchHandle.close();
            watchHandle = null;
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch") // background thread: log the failure, do not lose it
    private void importQuietly() {
        try {
            importExistingRuns();
        } catch (RuntimeException e) {
            log.error("Importing existing runs failed", e);
        }
    }

    /** Registers every run found in the data dir as an EXTERNAL analysis. */
    public synchronized ImportResult importExistingRuns() {
        List<RunHistoryEntry> history = scanRunHistoryPort.scan();
        // Newest first: the first completed run of a ticker and date is the one its files are from.
        Map<ReportKey, RunHistoryEntry> latestCompleted = new HashMap<>();
        history.stream()
                .filter(entry -> entry.status() == RunHistoryEntry.Status.COMPLETED)
                .forEach(entry -> latestCompleted.putIfAbsent(entry.key(), entry));
        List<ExternalAnalysis> externals = new ArrayList<>();
        Instant settledBefore = clock.instant().minus(settleTime);
        Instant lastUnsettled = null;
        for (Report report : scanReportsPort.scan()) {
            if (!checkReportDecisionPort.hasDecision(report)
                    && report.modifiedAt().isAfter(settledBefore)) {
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
                .map(this::fromHistory)
                .forEach(externals::add);
        validateExternalAnalysesPort.validate(externals);

        Map<ExternalRegistration.Outcome, Integer> outcomes = new EnumMap<>(ExternalRegistration.Outcome.class);
        // No platform user started these runs, so they belong to the first admin (none yet on a fresh install).
        OwnerId owner = userDirectoryPort
                .firstAdmin()
                .map(id -> new OwnerId(id.value()))
                .orElse(null);
        for (ExternalAnalysis external : externals) {
            outcomes.merge(
                    registerExternalAnalysisPort.register(external, owner).outcome(), 1, Integer::sum);
        }
        ImportResult result = new ImportResult(
                externals.size(),
                outcomes.getOrDefault(ExternalRegistration.Outcome.CREATED, 0),
                outcomes.getOrDefault(ExternalRegistration.Outcome.UPDATED, 0),
                outcomes.getOrDefault(ExternalRegistration.Outcome.UNCHANGED, 0));
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
            followUp =
                    followUpScheduledExecutorService.schedule(this::importQuietly, delayMillis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException _) {
            // Shutting down.
        }
    }

    private Instant max(Instant a, Instant b) {
        return a == null || b.isAfter(a) ? b : a;
    }

    private ExternalAnalysis toExternal(Report report, RunHistoryEntry run) {
        List<Analyst> analysts = new ArrayList<>();
        ANALYST_SECTIONS.forEach((section, analyst) -> {
            if (report.sections().containsKey(section)) {
                analysts.add(analyst);
            }
        });
        if (analysts.isEmpty() && run != null) {
            analysts.addAll(analysts(run));
        }
        Rating rating =
                report.rating() == null ? null : Rating.valueOf(report.rating().name());
        return new ExternalAnalysis(
                ExternalAnalysis.Origin.REPORT_FILES,
                report.key().ticker(),
                report.key().tradeDate(),
                analysts,
                rating,
                report.sections().get(ReportSection.FINAL_TRADE_DECISION),
                run == null ? null : toRun(run),
                run == null ? report.modifiedAt() : run.endedAt());
    }

    private ExternalAnalysis fromHistory(RunHistoryEntry entry) {
        return new ExternalAnalysis(
                ExternalAnalysis.Origin.RUN_HISTORY,
                entry.key().ticker(),
                entry.key().tradeDate(),
                analysts(entry),
                null,
                null,
                toRun(entry),
                entry.endedAt());
    }

    private ExternalRun toRun(RunHistoryEntry entry) {
        AnalysisStatus status = switch (entry.status()) {
            case COMPLETED -> AnalysisStatus.COMPLETED;
            case FAILED -> AnalysisStatus.FAILED;
            case STOPPED -> AnalysisStatus.STOPPED;
        };
        Integer depth = entry.researchDepth();
        return new ExternalRun(
                entry.id(),
                status,
                entry.error(),
                entry.llmProvider(),
                entry.deepThinkLlm(),
                entry.quickThinkLlm(),
                depth == null || depth < 1 ? 1 : depth,
                entry.outputLanguage(),
                new RunStats(
                        entry.llmCalls(),
                        entry.toolCalls(),
                        entry.tokensIn(),
                        entry.tokensOut(),
                        entry.costUsd(),
                        entry.elapsed()),
                entry.startedAt(),
                entry.endedAt());
    }

    private List<Analyst> analysts(RunHistoryEntry entry) {
        return entry.analysts().stream()
                .map(key -> ANALYST_KEYS.get(key.toLowerCase(Locale.ROOT)))
                .filter(Objects::nonNull)
                .toList();
    }
}
