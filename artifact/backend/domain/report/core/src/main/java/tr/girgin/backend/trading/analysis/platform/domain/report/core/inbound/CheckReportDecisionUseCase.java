package tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound;

import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.Report;

/** Tells whether a report already holds the final trade decision. */
public interface CheckReportDecisionUseCase {

    boolean hasDecision(Report report);
}
