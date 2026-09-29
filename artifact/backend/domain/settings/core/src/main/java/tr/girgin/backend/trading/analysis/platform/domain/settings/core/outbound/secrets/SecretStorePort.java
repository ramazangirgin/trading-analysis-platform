package tr.girgin.backend.trading.analysis.platform.domain.settings.core.outbound.secrets;

import java.util.Map;

public interface SecretStorePort {

    /** The platform's own secrets file. */
    Map<String, String> readManaged();

    /** Replaces the platform's secrets file's content (owner-only permissions). */
    void writeManaged(Map<String, String> secrets);

    /** The read-only files, merged in configuration order. */
    Map<String, String> readExternal();
}
