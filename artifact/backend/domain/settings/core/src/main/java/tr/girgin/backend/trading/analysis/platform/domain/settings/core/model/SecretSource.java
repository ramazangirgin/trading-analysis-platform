package tr.girgin.backend.trading.analysis.platform.domain.settings.core.model;

public enum SecretSource {
    /** The platform's own secrets file; the only one it writes. */
    PLATFORM,
    /** A read-only dotenv file configured next to it (e.g. TradingAgents' own .env). */
    EXTERNAL_FILE
}
