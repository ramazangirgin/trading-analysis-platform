package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.SettingsPersistenceConfiguration;

/** The one row of the {@code ANALYSIS_DEFAULTS} table. */
@Entity
@Table(name = "ANALYSIS_DEFAULTS", schema = SettingsPersistenceConfiguration.SCHEMA)
public class AnalysisDefaultsEntity {

    public static final String ROW_ID = "DEFAULTS";

    @Id
    @Column(name = "ID", nullable = false)
    private String id;

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

    // Optimistic lock: Hibernate sets it on insert, adds 1 on every update and fails a stale write.
    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;

    public AnalysisDefaultsEntity() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
