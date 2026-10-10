package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AssetType;

/** The columns of {@link AnalysisEntity} that make up the analysis' spec: what to analyze and with which models. */
@Embeddable
public class AnalysisSpecEmbeddable {

    @Column(name = "TICKER", nullable = false)
    private String ticker;

    @Column(name = "TRADE_DATE", nullable = false)
    private LocalDate tradeDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "ASSET_TYPE", nullable = false, columnDefinition = "\"ANALYSIS\".\"ASSET_TYPE\"")
    private AssetType assetType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ARRAY)
    // Hibernate binds the array of an enum as varchar[], which PostgreSQL rejects for an enum array column.
    @ColumnTransformer(write = "cast(? as \"ANALYSIS\".\"ANALYST\"[])")
    @Column(name = "ANALYSTS", nullable = false, columnDefinition = "\"ANALYSIS\".\"ANALYST\"[]")
    private List<Analyst> analysts;

    @Column(name = "LLM_PROVIDER", nullable = false)
    private String llmProvider;

    @Column(name = "DEEP_THINK_LLM", nullable = false)
    private String deepThinkLlm;

    @Column(name = "QUICK_THINK_LLM", nullable = false)
    private String quickThinkLlm;

    @Column(name = "MAX_DEBATE_ROUNDS", nullable = false)
    private int maxDebateRounds;

    @Column(name = "MAX_RISK_DISCUSS_ROUNDS", nullable = false)
    private int maxRiskDiscussRounds;

    @Column(name = "OUTPUT_LANGUAGE", nullable = false)
    private String outputLanguage;

    @Column(name = "CHECKPOINT_ENABLED", nullable = false)
    private boolean checkpointEnabled;

    public AnalysisSpecEmbeddable() {}

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public void setTradeDate(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
    }

    public AssetType getAssetType() {
        return assetType;
    }

    public void setAssetType(AssetType assetType) {
        this.assetType = assetType;
    }

    public List<Analyst> getAnalysts() {
        return analysts;
    }

    public void setAnalysts(List<Analyst> analysts) {
        this.analysts = analysts;
    }

    public String getLlmProvider() {
        return llmProvider;
    }

    public void setLlmProvider(String llmProvider) {
        this.llmProvider = llmProvider;
    }

    public String getDeepThinkLlm() {
        return deepThinkLlm;
    }

    public void setDeepThinkLlm(String deepThinkLlm) {
        this.deepThinkLlm = deepThinkLlm;
    }

    public String getQuickThinkLlm() {
        return quickThinkLlm;
    }

    public void setQuickThinkLlm(String quickThinkLlm) {
        this.quickThinkLlm = quickThinkLlm;
    }

    public int getMaxDebateRounds() {
        return maxDebateRounds;
    }

    public void setMaxDebateRounds(int maxDebateRounds) {
        this.maxDebateRounds = maxDebateRounds;
    }

    public int getMaxRiskDiscussRounds() {
        return maxRiskDiscussRounds;
    }

    public void setMaxRiskDiscussRounds(int maxRiskDiscussRounds) {
        this.maxRiskDiscussRounds = maxRiskDiscussRounds;
    }

    public String getOutputLanguage() {
        return outputLanguage;
    }

    public void setOutputLanguage(String outputLanguage) {
        this.outputLanguage = outputLanguage;
    }

    public boolean isCheckpointEnabled() {
        return checkpointEnabled;
    }

    public void setCheckpointEnabled(boolean checkpointEnabled) {
        this.checkpointEnabled = checkpointEnabled;
    }
}
