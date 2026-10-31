package tr.girgin.backend.trading.analysis.platform.domain.identity.core.model;

/** What a role may do. The key is what the database stores. */
public enum Permission {
    ANALYSIS_RUN("analysis:run"),
    ANALYSIS_READ("analysis:read"),
    ANALYSIS_READ_ALL("analysis:read:all"),
    ANALYSIS_DELETE("analysis:delete"),
    PRESET_READ("preset:read"),
    PRESET_MANAGE("preset:manage"),
    PRESET_READ_ALL("preset:read:all"),
    SETTINGS_READ("settings:read"),
    SETTINGS_KEYS_WRITE("settings:keys:write"),
    USER_READ("user:read"),
    USER_MANAGE("user:manage"),
    ROLE_MANAGE("role:manage"),
    AUDIT_READ("audit:read"),
    ANALYSIS_DEFAULTS_MANAGE("analysis:defaults:manage"),
    ANALYSIS_OVERRIDE_MODELS("analysis:override-models"),
    ANALYSIS_SHARE("analysis:share");

    private final String key;

    Permission(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
