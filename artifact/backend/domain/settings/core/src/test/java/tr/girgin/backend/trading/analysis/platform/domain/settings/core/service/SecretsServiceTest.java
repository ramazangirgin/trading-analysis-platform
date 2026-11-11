package tr.girgin.backend.trading.analysis.platform.domain.settings.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.exception.SettingsError;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.exception.SettingsException;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.SecretSource;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.SecretStatus;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.outbound.secrets.SecretStorePort;

class SecretsServiceTest {

    private final Map<String, String> managed = new LinkedHashMap<>();
    private final Map<String, String> external = new LinkedHashMap<>(
            Map.of("DEEPSEEK_API_KEY", "sk-external-1234", "TRADINGAGENTS_LLM_PROVIDER", "deepseek"));

    private final SecretsService secretsService = new SecretsService(new SecretStorePort() {
        @Override
        public Map<String, String> readManaged() {
            return Map.copyOf(managed);
        }

        @Override
        public void writeManaged(Map<String, String> secrets) {
            managed.clear();
            managed.putAll(secrets);
        }

        @Override
        public Map<String, String> readExternal() {
            return Map.copyOf(external);
        }
    });

    @Test
    void listsMaskedValuesWithThePlatformFileWinning() {
        secretsService.setSecret("DEEPSEEK_API_KEY", "  sk-platform-9876  ");
        secretsService.setSecret("OPENAI_API_KEY", "sk-openai-abcd");

        assertThat(secretsService.listSecrets())
                .containsExactly(
                        new SecretStatus("DEEPSEEK_API_KEY", SecretSource.PLATFORM, "••••9876"),
                        new SecretStatus("OPENAI_API_KEY", SecretSource.PLATFORM, "••••abcd"),
                        new SecretStatus("TRADINGAGENTS_LLM_PROVIDER", SecretSource.EXTERNAL_FILE, "••••"));
        assertThat(managed).containsEntry("DEEPSEEK_API_KEY", "sk-platform-9876");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PATH", "PYTHONPATH", "DYLD_INSERT_LIBRARIES", "deepseek_api_key", "X_API_KEY\nPATH"})
    void refusesNamesThatAreNotProviderCredentials(String name) {
        assertThatThrownBy(() -> secretsService.setSecret(name, "x"))
                .isInstanceOfSatisfying(
                        SettingsException.class,
                        e -> assertThat(e.error()).isEqualTo(SettingsError.INVALID_SECRET_NAME));
        assertThat(managed).isEmpty();
    }

    @Test
    void refusesEmptyOrMultiLineValues() {
        assertThatThrownBy(() -> secretsService.setSecret("OPENAI_API_KEY", " "))
                .isInstanceOf(SettingsException.class);
        assertThatThrownBy(() -> secretsService.setSecret("OPENAI_API_KEY", "sk-1\nPATH=/tmp"))
                .isInstanceOf(SettingsException.class);
    }

    @Test
    void removesOnlyFromThePlatformFile() {
        secretsService.setSecret("OPENAI_API_KEY", "sk-openai-abcd");

        secretsService.removeSecret("OPENAI_API_KEY");

        assertThat(managed).isEmpty();
        assertThatThrownBy(() -> secretsService.removeSecret("DEEPSEEK_API_KEY"))
                .isInstanceOfSatisfying(
                        SettingsException.class,
                        e -> assertThat(e.error()).isEqualTo(SettingsError.SECRET_NOT_MANAGED));
        assertThat(external).containsKey("DEEPSEEK_API_KEY");
    }
}
