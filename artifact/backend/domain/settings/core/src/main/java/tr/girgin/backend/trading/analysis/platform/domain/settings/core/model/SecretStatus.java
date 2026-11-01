package tr.girgin.backend.trading.analysis.platform.domain.settings.core.model;

/** Whether a secret is set and where from. The value never leaves the backend: only its last characters. */
public record SecretStatus(String name, SecretSource source, String maskedValue) {}
