package tr.girgin.backend.trading.analysis.platform.domain.report.core.service;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound.ScanRunHistoryUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.RunHistoryEntry;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.outbound.history.RunHistoryPort;

@Service
class RunHistoryService implements ScanRunHistoryUseCase {

    private final RunHistoryPort history;

    RunHistoryService(RunHistoryPort history) {
        this.history = history;
    }

    @Override
    public List<RunHistoryEntry> scan() {
        return history.readAll().stream()
                .sorted(Comparator.comparing(RunHistoryEntry::endedAt).reversed())
                .toList();
    }
}
