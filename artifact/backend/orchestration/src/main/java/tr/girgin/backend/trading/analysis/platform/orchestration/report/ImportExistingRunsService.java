package tr.girgin.backend.trading.analysis.platform.orchestration.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.RegisterExternalAnalysisUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalAnalysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalRegistration;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Rating;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound.ScanReportsUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.Report;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportSection;

/** report finds runs in the data dir; analysis records them as EXTERNAL analyses. */
@Service
class ImportExistingRunsService implements ImportExistingRunsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ImportExistingRunsService.class);

    private static final Map<ReportSection, Analyst> ANALYST_SECTIONS = Map.of(
            ReportSection.MARKET_REPORT, Analyst.MARKET,
            ReportSection.SENTIMENT_REPORT, Analyst.SOCIAL,
            ReportSection.NEWS_REPORT, Analyst.NEWS,
            ReportSection.FUNDAMENTALS_REPORT, Analyst.FUNDAMENTALS);

    private final ScanReportsUseCase scanReports;
    private final RegisterExternalAnalysisUseCase registerExternal;
    private final boolean importOnStartup;

    ImportExistingRunsService(ScanReportsUseCase scanReports,
                              RegisterExternalAnalysisUseCase registerExternal,
                              @Value("${platform.import.on-startup:true}") boolean importOnStartup) {
        this.scanReports = scanReports;
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
        List<Report> reports = scanReports.scan();
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        for (Report report : reports) {
            ExternalRegistration registration = registerExternal.register(toExternal(report));
            switch (registration.outcome()) {
                case CREATED -> created++;
                case UPDATED -> updated++;
                case UNCHANGED -> unchanged++;
            }
        }
        ImportResult result = new ImportResult(reports.size(), created, updated, unchanged);
        log.info("Imported existing runs: {}", result);
        return result;
    }

    private static ExternalAnalysis toExternal(Report report) {
        List<Analyst> analysts = new ArrayList<>();
        ANALYST_SECTIONS.forEach((section, analyst) -> {
            if (report.sections().containsKey(section)) {
                analysts.add(analyst);
            }
        });
        Rating rating = report.rating() == null ? null : Rating.valueOf(report.rating().name());
        return new ExternalAnalysis(report.key().ticker(), report.key().tradeDate(), analysts, rating,
                report.sections().get(ReportSection.FINAL_TRADE_DECISION), report.modifiedAt());
    }
}
