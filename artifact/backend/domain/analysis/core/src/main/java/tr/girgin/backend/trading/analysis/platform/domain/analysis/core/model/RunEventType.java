package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

/** Event types of the runner protocol (docs/event-protocol.md, section 4). */
public enum RunEventType {
    RUN_STARTED,
    AGENT_STATUS,
    MESSAGE,
    TOOL_CALL,
    TOOL_RESULT,
    REPORT_SECTION,
    DEBATE,
    STATS,
    LOG,
    DECISION,
    RUN_FINISHED
}
