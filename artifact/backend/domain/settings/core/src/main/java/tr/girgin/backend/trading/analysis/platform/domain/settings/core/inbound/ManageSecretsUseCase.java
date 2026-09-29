package tr.girgin.backend.trading.analysis.platform.domain.settings.core.inbound;

import java.util.List;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.SecretStatus;

public interface ManageSecretsUseCase {

    /** Every secret set anywhere, by name; a name set in several places is reported once, as the runner sees it. */
    List<SecretStatus> listSecrets();

    /** Stores the value in the platform's secrets file. Only provider keys and endpoints may be set. */
    SecretStatus setSecret(String name, String value);

    /** Removes the value from the platform's secrets file; read-only files are not touched. */
    void removeSecret(String name);
}
