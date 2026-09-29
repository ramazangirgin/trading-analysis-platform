package tr.girgin.backend.trading.analysis.platform.domain.report.core.model;

/** Where a report's content was found in the data dir. */
public enum ReportSource {
    /** {@code logs/<T>/<D>/reports/**.md}, written by the CLI and ta-runner. */
    REPORT_TREE,
    /** {@code logs/<T>/TradingAgentsStrategy_logs/full_states_log_<D>.json}. */
    FULL_STATE,
    /** {@code logs/<T>/<D>/reports/run.json}, written by third-party UIs. */
    GUI_RUN
}
