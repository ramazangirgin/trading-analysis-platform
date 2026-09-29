package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.GetAnalysisUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.ReadAnalysisLogsUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runlog.RunLogPort;

/** Serves run logs with anything that looks like an API key masked (PLAN.md section 7). */
@Service
class AnalysisLogService implements ReadAnalysisLogsUseCase {

    static final int MAX_LINES = 5_000;
    // sk-..., sk-ant-..., AIza... and long bearer-style tokens.
    private static final Pattern KEY = Pattern.compile("\\b(sk-[A-Za-z]*-?|AIza|xai-|gsk_)([A-Za-z0-9_\\-]{4})[A-Za-z0-9_\\-]{8,}");

    private final GetAnalysisUseCase getAnalysis;
    private final RunLogPort runLog;

    AnalysisLogService(GetAnalysisUseCase getAnalysis, RunLogPort runLog) {
        this.getAnalysis = getAnalysis;
        this.runLog = runLog;
    }

    @Override
    public List<String> tailLogs(AnalysisId id, int maxLines) {
        getAnalysis.get(id);
        int lines = Math.clamp(maxLines, 1, MAX_LINES);
        return runLog.tail(id, lines).stream().map(AnalysisLogService::mask).toList();
    }

    static String mask(String line) {
        return KEY.matcher(line).replaceAll("$1$2****");
    }
}
