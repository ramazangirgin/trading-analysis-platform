package tr.girgin.backend.trading.analysis.platform.domain.report.core.service;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound.ScanRunHistoryPort;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.RunHistoryEntry;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.outbound.history.RunHistoryPort;

@Service
class RunHistoryService implements ScanRunHistoryPort {

    private final RunHistoryPort history;
    private final ReportKeyService reportKeyService;

    RunHistoryService(RunHistoryPort history, ReportKeyService reportKeyService) {
        this.history = history;
        this.reportKeyService = reportKeyService;
    }

    @Override
    public List<RunHistoryEntry> scan() {
        return history.readAll().stream()
                .filter(entry -> reportKeyService.isValid(entry.key()))
                .sorted(Comparator.comparing(RunHistoryEntry::endedAt).reversed())
                .toList();
    }
}
