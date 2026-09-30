package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisFilter;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.persistence.AnalysisRepositoryPort;

@Component
class JdbcAnalysisRepositoryAdapter implements AnalysisRepositoryPort {

    private static final int LIST_LIMIT = 500;

    private static final String INSERT = """
            INSERT INTO analyses (id, ticker, trade_date, asset_type, analysts, llm_provider, deep_think_llm,
                quick_think_llm, max_debate_rounds, max_risk_discuss_rounds, output_language, checkpoint_enabled,
                status, source, rating, decision, llm_calls, tool_calls, tokens_in, tokens_out, cost_usd,
                elapsed_ms, created_at, started_at, ended_at, error_code, error_message, external_ref, runner_ref)
            VALUES (:id, :ticker, :tradeDate, :assetType, :analysts, :llmProvider, :deepThinkLlm,
                :quickThinkLlm, :maxDebateRounds, :maxRiskDiscussRounds, :outputLanguage, :checkpointEnabled,
                :status, :source, :rating, :decision, :llmCalls, :toolCalls, :tokensIn, :tokensOut, :costUsd,
                :elapsedMs, :createdAt, :startedAt, :endedAt, :errorCode, :errorMessage, :externalRef, :runnerRef)
            """;

    // The spec and creation time never change after insert.
    private static final String UPDATE = """
            UPDATE analyses SET status = :status, rating = :rating, decision = :decision,
                llm_calls = :llmCalls, tool_calls = :toolCalls, tokens_in = :tokensIn, tokens_out = :tokensOut,
                cost_usd = :costUsd, elapsed_ms = :elapsedMs, started_at = :startedAt, ended_at = :endedAt,
                error_code = :errorCode, error_message = :errorMessage, runner_ref = :runnerRef
            WHERE id = :id
            """;

    // An imported record follows its files, spec and creation time included.
    private static final String REPLACE_IMPORTED = """
            UPDATE analyses SET ticker = :ticker, trade_date = :tradeDate, asset_type = :assetType,
                analysts = :analysts, llm_provider = :llmProvider, deep_think_llm = :deepThinkLlm,
                quick_think_llm = :quickThinkLlm, max_debate_rounds = :maxDebateRounds,
                max_risk_discuss_rounds = :maxRiskDiscussRounds, output_language = :outputLanguage,
                checkpoint_enabled = :checkpointEnabled, status = :status, rating = :rating, decision = :decision,
                llm_calls = :llmCalls, tool_calls = :toolCalls, tokens_in = :tokensIn, tokens_out = :tokensOut,
                cost_usd = :costUsd, elapsed_ms = :elapsedMs, created_at = :createdAt, started_at = :startedAt,
                ended_at = :endedAt, error_code = :errorCode, error_message = :errorMessage,
                external_ref = :externalRef
            WHERE id = :id AND source = 'EXTERNAL'
            """;

    private final JdbcClient jdbc;
    private final AnalysisToAnalysisRowMapper toRow;
    private final AnalysisRowToAnalysisMapper toAnalysis;

    JdbcAnalysisRepositoryAdapter(JdbcClient jdbc,
                                  AnalysisToAnalysisRowMapper toRow,
                                  AnalysisRowToAnalysisMapper toAnalysis) {
        this.jdbc = jdbc;
        this.toRow = toRow;
        this.toAnalysis = toAnalysis;
    }

    @Override
    public void insert(Analysis analysis) {
        jdbc.sql(INSERT).paramSource(toRow.map(analysis)).update();
    }

    @Override
    public void update(Analysis analysis) {
        int updated = jdbc.sql(UPDATE).paramSource(toRow.map(analysis)).update();
        if (updated != 1) {
            throw new IllegalStateException("No analysis " + analysis.id() + " to update");
        }
    }

    @Override
    public void replaceImported(Analysis analysis) {
        int updated = jdbc.sql(REPLACE_IMPORTED).paramSource(toRow.map(analysis)).update();
        if (updated != 1) {
            throw new IllegalStateException("No imported analysis " + analysis.id() + " to replace");
        }
    }

    @Override
    public Optional<Analysis> findByExternalRef(String externalRef) {
        return jdbc.sql("SELECT * FROM analyses WHERE external_ref = :externalRef")
                .param("externalRef", externalRef)
                .query(AnalysisRow.class)
                .optional()
                .map(toAnalysis::map);
    }

    @Override
    public Optional<Analysis> findById(AnalysisId id) {
        return jdbc.sql("SELECT * FROM analyses WHERE id = :id")
                .param("id", id.value())
                .query(AnalysisRow.class)
                .optional()
                .map(toAnalysis::map);
    }

    @Override
    public List<Analysis> findAll(AnalysisFilter filter) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        if (filter.status() != null) {
            conditions.add("status = :status");
            params.put("status", filter.status().name());
        }
        if (filter.ticker() != null) {
            conditions.add("ticker = :ticker");
            params.put("ticker", filter.ticker());
        }
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        return jdbc.sql("SELECT * FROM analyses" + where + " ORDER BY created_at DESC LIMIT " + LIST_LIMIT)
                .params(params)
                .query(AnalysisRow.class)
                .list()
                .stream()
                .map(toAnalysis::map)
                .toList();
    }

    @Override
    public List<Analysis> findByStatusIn(Collection<AnalysisStatus> statuses) {
        if (statuses.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT * FROM analyses WHERE status IN (:statuses)")
                .param("statuses", statuses.stream().map(Enum::name).toList())
                .query(AnalysisRow.class)
                .list()
                .stream()
                .map(toAnalysis::map)
                .toList();
    }
}
