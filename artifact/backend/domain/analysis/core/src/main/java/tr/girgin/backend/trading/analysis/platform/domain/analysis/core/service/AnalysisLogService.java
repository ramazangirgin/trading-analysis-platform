package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisError;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.GetAnalysisUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.ReadAnalysisLogsUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisAccess;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runlog.RunLogPort;

/** Serves run logs with anything that looks like an API key masked. */
@Service
class AnalysisLogService implements ReadAnalysisLogsUseCase {

    static final int MAX_LINES = 5_000;
    // sk-..., sk-ant-..., AIza... and long bearer-style tokens.
    private static final Pattern KEY =
            Pattern.compile("\\b(sk-[A-Za-z]*-?|AIza|xai-|gsk_)([A-Za-z0-9_\\-]{4})[A-Za-z0-9_\\-]{8,}");

    private final GetAnalysisUseCase getAnalysis;
    private final RunLogPort runLog;
    private final AnalysisAccessService accessRules;

    AnalysisLogService(GetAnalysisUseCase getAnalysis, RunLogPort runLog, AnalysisAccessService accessRules) {
        this.getAnalysis = getAnalysis;
        this.runLog = runLog;
        this.accessRules = accessRules;
    }

    @Override
    public List<String> tailLogs(AnalysisId id, AnalysisAccess access, int maxLines) {
        Analysis analysis = getAnalysis.get(id, access);
        // The log can hold internal details, so a reader of a public analysis does not get it.
        if (!accessRules.canManage(analysis, access)) {
            throw new AnalysisException(
                    AnalysisError.NOT_FOUND, "Analysis not found: " + id.value(), Map.of("id", id.value()));
        }
        int lines = Math.clamp(maxLines, 1, MAX_LINES);
        return runLog.tail(id, lines).stream().map(this::mask).toList();
    }

    String mask(String line) {
        return KEY.matcher(line).replaceAll("$1$2****");
    }
}
