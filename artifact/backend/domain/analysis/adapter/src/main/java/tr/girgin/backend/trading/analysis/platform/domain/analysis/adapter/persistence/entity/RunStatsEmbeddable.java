package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.time.Duration;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The columns of {@link AnalysisEntity} that hold the run's LLM usage and wall time. */
@Embeddable
public class RunStatsEmbeddable {

    @Column(name = "LLM_CALLS", nullable = false)
    private long llmCalls;

    @Column(name = "TOOL_CALLS", nullable = false)
    private long toolCalls;

    @Column(name = "TOKENS_IN", nullable = false)
    private long tokensIn;

    @Column(name = "TOKENS_OUT", nullable = false)
    private long tokensOut;

    @Column(name = "COST_USD")
    private BigDecimal costUsd;

    @Column(name = "ELAPSED", nullable = false)
    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    private Duration elapsed;

    public RunStatsEmbeddable() {}

    public long getLlmCalls() {
        return llmCalls;
    }

    public void setLlmCalls(long llmCalls) {
        this.llmCalls = llmCalls;
    }

    public long getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(long toolCalls) {
        this.toolCalls = toolCalls;
    }

    public long getTokensIn() {
        return tokensIn;
    }

    public void setTokensIn(long tokensIn) {
        this.tokensIn = tokensIn;
    }

    public long getTokensOut() {
        return tokensOut;
    }

    public void setTokensOut(long tokensOut) {
        this.tokensOut = tokensOut;
    }

    public BigDecimal getCostUsd() {
        return costUsd;
    }

    public void setCostUsd(BigDecimal costUsd) {
        this.costUsd = costUsd;
    }

    public Duration getElapsed() {
        return elapsed;
    }

    public void setElapsed(Duration elapsed) {
        this.elapsed = elapsed;
    }
}
