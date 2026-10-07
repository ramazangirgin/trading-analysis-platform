package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.mapper.AnalysisRowToAnalysisMapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.mapper.AnalysisToAnalysisRowMapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.row.AnalysisRow;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisFilter;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.persistence.AnalysisRepositoryPort;

@Component
class JdbcAnalysisRepositoryAdapter implements AnalysisRepositoryPort {

    private static final int LIST_LIMIT = 500;

    private static final String INSERT = """
            INSERT INTO "ANALYSES" ("ID", "TICKER", "TRADE_DATE", "ASSET_TYPE", "ANALYSTS", "LLM_PROVIDER",
                "DEEP_THINK_LLM", "QUICK_THINK_LLM", "MAX_DEBATE_ROUNDS", "MAX_RISK_DISCUSS_ROUNDS",
                "OUTPUT_LANGUAGE", "CHECKPOINT_ENABLED", "STATUS", "SOURCE", "RATING", "DECISION", "LLM_CALLS",
                "TOOL_CALLS", "TOKENS_IN", "TOKENS_OUT", "COST_USD", "ELAPSED_MS", "CREATED_AT", "STARTED_AT",
                "ENDED_AT", "ERROR_CODE", "ERROR_MESSAGE", "EXTERNAL_REF", "RUNNER_REF")
            VALUES (:id, :ticker, :tradeDate, :assetType, :analysts, :llmProvider, :deepThinkLlm,
                :quickThinkLlm, :maxDebateRounds, :maxRiskDiscussRounds, :outputLanguage, :checkpointEnabled,
                :status, :source, :rating, :decision, :llmCalls, :toolCalls, :tokensIn, :tokensOut, :costUsd,
                :elapsedMs, :createdAt, :startedAt, :endedAt, :errorCode, :errorMessage, :externalRef, :runnerRef)
            """;

    // The spec and creation time never change after insert.
    private static final String UPDATE = """
            UPDATE "ANALYSES" SET "STATUS" = :status, "RATING" = :rating, "DECISION" = :decision,
                "LLM_CALLS" = :llmCalls, "TOOL_CALLS" = :toolCalls, "TOKENS_IN" = :tokensIn,
                "TOKENS_OUT" = :tokensOut, "COST_USD" = :costUsd, "ELAPSED_MS" = :elapsedMs,
                "STARTED_AT" = :startedAt, "ENDED_AT" = :endedAt, "ERROR_CODE" = :errorCode,
                "ERROR_MESSAGE" = :errorMessage, "RUNNER_REF" = :runnerRef
            WHERE "ID" = :id
            """;

    // An imported record follows its files, spec and creation time included.
    private static final String REPLACE_IMPORTED = """
            UPDATE "ANALYSES" SET "TICKER" = :ticker, "TRADE_DATE" = :tradeDate, "ASSET_TYPE" = :assetType,
                "ANALYSTS" = :analysts, "LLM_PROVIDER" = :llmProvider, "DEEP_THINK_LLM" = :deepThinkLlm,
                "QUICK_THINK_LLM" = :quickThinkLlm, "MAX_DEBATE_ROUNDS" = :maxDebateRounds,
                "MAX_RISK_DISCUSS_ROUNDS" = :maxRiskDiscussRounds, "OUTPUT_LANGUAGE" = :outputLanguage,
                "CHECKPOINT_ENABLED" = :checkpointEnabled, "STATUS" = :status, "RATING" = :rating,
                "DECISION" = :decision, "LLM_CALLS" = :llmCalls, "TOOL_CALLS" = :toolCalls,
                "TOKENS_IN" = :tokensIn, "TOKENS_OUT" = :tokensOut, "COST_USD" = :costUsd,
                "ELAPSED_MS" = :elapsedMs, "CREATED_AT" = :createdAt, "STARTED_AT" = :startedAt,
                "ENDED_AT" = :endedAt, "ERROR_CODE" = :errorCode, "ERROR_MESSAGE" = :errorMessage,
                "EXTERNAL_REF" = :externalRef
            WHERE "ID" = :id AND "SOURCE" = 'EXTERNAL'
            """;

    private final JdbcClient jdbc;
    private final AnalysisToAnalysisRowMapper toRow;
    private final AnalysisRowToAnalysisMapper toAnalysis;

    JdbcAnalysisRepositoryAdapter(
            JdbcClient jdbc, AnalysisToAnalysisRowMapper toRow, AnalysisRowToAnalysisMapper toAnalysis) {
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
        int updated =
                jdbc.sql(REPLACE_IMPORTED).paramSource(toRow.map(analysis)).update();
        if (updated != 1) {
            throw new IllegalStateException("No imported analysis " + analysis.id() + " to replace");
        }
    }

    @Override
    public Optional<Analysis> findByExternalRef(String externalRef) {
        return jdbc.sql("SELECT * FROM \"ANALYSES\" WHERE \"EXTERNAL_REF\" = :externalRef")
                .param("externalRef", externalRef)
                .query(AnalysisRow.class)
                .optional()
                .map(toAnalysis::map);
    }

    @Override
    public Optional<Analysis> findById(AnalysisId id) {
        return jdbc.sql("SELECT * FROM \"ANALYSES\" WHERE \"ID\" = :id")
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
            conditions.add("\"STATUS\" = :status");
            params.put("status", filter.status().name());
        }
        if (filter.ticker() != null) {
            conditions.add("\"TICKER\" = :ticker");
            params.put("ticker", filter.ticker());
        }
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        return jdbc
                .sql("SELECT * FROM \"ANALYSES\"" + where + " ORDER BY \"CREATED_AT\" DESC LIMIT " + LIST_LIMIT)
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
        return jdbc
                .sql("SELECT * FROM \"ANALYSES\" WHERE \"STATUS\" IN (:statuses)")
                .param("statuses", statuses.stream().map(Enum::name).toList())
                .query(AnalysisRow.class)
                .list()
                .stream()
                .map(toAnalysis::map)
                .toList();
    }
}
