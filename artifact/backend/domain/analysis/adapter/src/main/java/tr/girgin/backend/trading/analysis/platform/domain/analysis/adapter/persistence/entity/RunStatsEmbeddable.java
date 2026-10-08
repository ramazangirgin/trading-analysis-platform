package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

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
    private Double costUsd;

    @Column(name = "ELAPSED_MS", nullable = false)
    private long elapsedMs;

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

    public Double getCostUsd() {
        return costUsd;
    }

    public void setCostUsd(Double costUsd) {
        this.costUsd = costUsd;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }
}
