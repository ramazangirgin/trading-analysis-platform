package tr.girgin.backend.trading.analysis.platform.domain.report.adapter.datadir;

/** {@code full_states_log_<date>.json}, as upstream's {@code _log_state} writes it. */
record FullStateLog(
        String marketReport,
        String sentimentReport,
        String newsReport,
        String fundamentalsReport,
        InvestmentDebate investmentDebateState,
        String traderInvestmentDecision,
        RiskDebate riskDebateState,
        String investmentPlan,
        String finalTradeDecision) {

    record InvestmentDebate(String bullHistory, String bearHistory, String judgeDecision) {
    }

    record RiskDebate(String aggressiveHistory, String conservativeHistory, String neutralHistory,
                      String judgeDecision) {
    }
}
