package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception;

/** Error codes; the frontend translates them. */
public enum AnalysisError {
    INVALID_SPEC,
    NOT_FOUND,
    ALREADY_RUNNING,
    NOT_RUNNING,
    CONCURRENT_UPDATE,
    OVERRIDE_FORBIDDEN,
    NOT_SHAREABLE,
    SHARE_FORBIDDEN
}
