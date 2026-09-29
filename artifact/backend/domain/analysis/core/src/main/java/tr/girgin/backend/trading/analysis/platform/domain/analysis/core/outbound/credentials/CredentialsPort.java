package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.credentials;

import java.util.Map;

/** Provider API keys, handed to the runner as environment variables. Never logged. */
public interface CredentialsPort {

    Map<String, String> environment();
}
