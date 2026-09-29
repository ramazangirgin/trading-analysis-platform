package tr.girgin.backend.trading.analysis.platform.domain.settings.core.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.inbound.ManageSecretsUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.SecretSource;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.SecretStatus;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.SettingsError;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.SettingsException;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.outbound.secrets.SecretStorePort;

/**
 * Provider keys for the runner. Only names that look like provider credentials or endpoints can
 * be written: these files become the runner's environment, so PATH or PYTHONPATH must never be.
 */
@Service
class SecretsService implements ManageSecretsUseCase {

    private static final Pattern WRITABLE_NAME = Pattern.compile("[A-Z][A-Z0-9_]{0,60}_(API_KEY|BASE_URL)");
    private static final int MAX_VALUE_LENGTH = 4096;
    private static final int VISIBLE_SUFFIX = 4;

    private final SecretStorePort store;

    SecretsService(SecretStorePort store) {
        this.store = store;
    }

    @Override
    public List<SecretStatus> listSecrets() {
        Map<String, SecretStatus> statuses = new TreeMap<>();
        store.readExternal().forEach((name, value) ->
                statuses.put(name, new SecretStatus(name, SecretSource.EXTERNAL_FILE, mask(value))));
        // The platform's file wins, as it does when the runner's environment is built.
        store.readManaged().forEach((name, value) ->
                statuses.put(name, new SecretStatus(name, SecretSource.PLATFORM, mask(value))));
        return new ArrayList<>(statuses.values());
    }

    @Override
    public synchronized SecretStatus setSecret(String name, String value) {
        requireWritable(name);
        String trimmed = value == null ? "" : value.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_VALUE_LENGTH || trimmed.chars().anyMatch(Character::isISOControl)) {
            throw new SettingsException(SettingsError.INVALID_SECRET_VALUE, "Invalid value for " + name,
                    Map.of("name", name));
        }
        Map<String, String> managed = new LinkedHashMap<>(store.readManaged());
        managed.put(name, trimmed);
        store.writeManaged(managed);
        return new SecretStatus(name, SecretSource.PLATFORM, mask(trimmed));
    }

    @Override
    public synchronized void removeSecret(String name) {
        requireWritable(name);
        Map<String, String> managed = new LinkedHashMap<>(store.readManaged());
        if (managed.remove(name) == null) {
            throw new SettingsException(SettingsError.SECRET_NOT_MANAGED,
                    name + " is not set in the platform's secrets file", Map.of("name", name));
        }
        store.writeManaged(managed);
    }

    private static void requireWritable(String name) {
        if (name == null || !WRITABLE_NAME.matcher(name).matches()) {
            throw new SettingsException(SettingsError.INVALID_SECRET_NAME,
                    "Only *_API_KEY and *_BASE_URL names can be set", Map.of("name", String.valueOf(name)));
        }
    }

    static String mask(String value) {
        if (value.length() <= VISIBLE_SUFFIX * 2) {
            return "••••";
        }
        return "••••" + value.substring(value.length() - VISIBLE_SUFFIX);
    }
}
