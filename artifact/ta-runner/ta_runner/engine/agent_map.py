"""Upstream state keys -> agents and report sections (mirrors the upstream CLI's display)."""

ANALYST_AGENTS = {
    "market": "Market Analyst",
    "social": "Sentiment Analyst",
    "news": "News Analyst",
    "fundamentals": "Fundamentals Analyst",
}

ANALYST_REPORTS = {
    "market": "market_report",
    "social": "sentiment_report",
    "news": "news_report",
    "fundamentals": "fundamentals_report",
}

# Top-level report sections in pipeline order, with the agent that writes each.
REPORT_SECTIONS = {
    "market_report": "Market Analyst",
    "sentiment_report": "Sentiment Analyst",
    "news_report": "News Analyst",
    "fundamentals_report": "Fundamentals Analyst",
    "investment_plan": "Research Manager",
    "trader_investment_plan": "Trader",
    "final_trade_decision": "Portfolio Manager",
}

# (state key, history field, speaker) for the two debates.
DEBATE_FIELDS = (
    ("investment_debate_state", "bull_history", "bull"),
    ("investment_debate_state", "bear_history", "bear"),
    ("investment_debate_state", "judge_decision", "judge"),
    ("risk_debate_state", "aggressive_history", "aggressive"),
    ("risk_debate_state", "conservative_history", "conservative"),
    ("risk_debate_state", "neutral_history", "neutral"),
    ("risk_debate_state", "judge_decision", "judge"),
)
