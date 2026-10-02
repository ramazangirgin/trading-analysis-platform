package tr.girgin.backend.trading.analysis.platform.domain.report.adapter.datadir.json;

/** {@code full_states_log_<date>.json}, as upstream's {@code _log_state} writes it. */
public record FullStateLog(
        String marketReport,
        String sentimentReport,
        String newsReport,
        String fundamentalsReport,
        InvestmentDebate investmentDebateState,
        String traderInvestmentDecision,
        RiskDebate riskDebateState,
        String investmentPlan,
        String finalTradeDecision) {

    public record InvestmentDebate(String bullHistory, String bearHistory, String judgeDecision) {
    }

    public record RiskDebate(String aggressiveHistory, String conservativeHistory, String neutralHistory,
                      String judgeDecision) {
    }
}
