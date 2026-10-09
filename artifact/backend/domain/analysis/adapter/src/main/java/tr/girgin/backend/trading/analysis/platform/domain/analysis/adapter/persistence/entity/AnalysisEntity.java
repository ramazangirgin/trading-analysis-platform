package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.AnalysisPersistenceConfiguration;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSource;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Rating;

/**
 * One row of the {@code ANALYSES} table. {@link Persistable} with a transient flag: an entity the
 * mapper built is new, one the database returned is not, so an insert is a plain {@code persist}
 * (it fails on an existing ID, without the {@code SELECT} a {@code merge} would add).
 */
@Entity
@Table(name = "ANALYSES", schema = AnalysisPersistenceConfiguration.SCHEMA)
public class AnalysisEntity implements Persistable<AnalysisIdEmbeddable> {

    @EmbeddedId
    private AnalysisIdEmbeddable id;

    @Embedded
    private AnalysisSpecEmbeddable spec;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "STATUS", nullable = false, columnDefinition = "\"ANALYSIS\".\"ANALYSIS_STATUS\"")
    private AnalysisStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "SOURCE", nullable = false, columnDefinition = "\"ANALYSIS\".\"ANALYSIS_SOURCE\"")
    private AnalysisSource source;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "RATING", columnDefinition = "\"ANALYSIS\".\"RATING\"")
    private Rating rating;

    @Column(name = "DECISION")
    private String decision;

    @Embedded
    private RunStatsEmbeddable stats;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "STARTED_AT")
    private Instant startedAt;

    @Column(name = "ENDED_AT")
    private Instant endedAt;

    @Column(name = "ERROR_CODE")
    private String errorCode;

    @Column(name = "ERROR_MESSAGE")
    private String errorMessage;

    @Column(name = "EXTERNAL_REF")
    private String externalRef;

    @Column(name = "RUNNER_REF")
    private String runnerRef;

    @Transient
    private boolean newEntity = true;

    public AnalysisEntity() {}

    /** Copies the state a run changes onto this entity; the spec, creation time, source and external ref stay. */
    public void applyRunState(AnalysisEntity other) {
        this.status = other.status;
        this.rating = other.rating;
        this.decision = other.decision;
        this.stats = other.stats;
        this.startedAt = other.startedAt;
        this.endedAt = other.endedAt;
        this.errorCode = other.errorCode;
        this.errorMessage = other.errorMessage;
        this.runnerRef = other.runnerRef;
    }

    /** Copies everything an import follows from the data dir: all but the ID, the source and the runner ref. */
    public void replaceImported(AnalysisEntity other) {
        this.spec = other.spec;
        this.status = other.status;
        this.rating = other.rating;
        this.decision = other.decision;
        this.stats = other.stats;
        this.createdAt = other.createdAt;
        this.startedAt = other.startedAt;
        this.endedAt = other.endedAt;
        this.errorCode = other.errorCode;
        this.errorMessage = other.errorMessage;
        this.externalRef = other.externalRef;
    }

    @PostLoad
    @PostPersist
    void markStored() {
        this.newEntity = false;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @Override
    public AnalysisIdEmbeddable getId() {
        return id;
    }

    public void setId(AnalysisIdEmbeddable id) {
        this.id = id;
    }

    public AnalysisSpecEmbeddable getSpec() {
        return spec;
    }

    public void setSpec(AnalysisSpecEmbeddable spec) {
        this.spec = spec;
    }

    public AnalysisStatus getStatus() {
        return status;
    }

    public void setStatus(AnalysisStatus status) {
        this.status = status;
    }

    public AnalysisSource getSource() {
        return source;
    }

    public void setSource(AnalysisSource source) {
        this.source = source;
    }

    public Rating getRating() {
        return rating;
    }

    public void setRating(Rating rating) {
        this.rating = rating;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public RunStatsEmbeddable getStats() {
        return stats;
    }

    public void setStats(RunStatsEmbeddable stats) {
        this.stats = stats;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public void setExternalRef(String externalRef) {
        this.externalRef = externalRef;
    }

    public String getRunnerRef() {
        return runnerRef;
    }

    public void setRunnerRef(String runnerRef) {
        this.runnerRef = runnerRef;
    }
}
