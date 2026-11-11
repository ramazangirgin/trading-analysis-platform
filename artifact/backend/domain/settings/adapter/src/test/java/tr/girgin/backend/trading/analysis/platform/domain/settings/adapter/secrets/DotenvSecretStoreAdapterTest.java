package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.secrets;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DotenvSecretStoreAdapterTest {

    @TempDir
    private Path dir;

    @Test
    void writesOwnerOnlyAndReadsBackTheSameValues() throws Exception {
        Path managed = dir.resolve("home/secrets.env");
        DotenvSecretStoreAdapter dotenvSecretStoreAdapter = new DotenvSecretStoreAdapter(managed, new String[0]);
        Map<String, String> secrets = new LinkedHashMap<>();
        secrets.put("OPENAI_API_KEY", "sk-with\"quote and \\ backslash");
        secrets.put("OLLAMA_BASE_URL", "http://localhost:11434/v1");

        dotenvSecretStoreAdapter.writeManaged(secrets);

        assertThat(dotenvSecretStoreAdapter.readManaged()).isEqualTo(secrets);
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(managed)))
                .isEqualTo("rw-------");
        assertThat(Files.list(managed.getParent())).containsExactly(managed);
    }

    @Test
    void mergesExternalFilesInOrderAndNeverWritesThem() throws Exception {
        Path first = dir.resolve("first.env");
        Path second = dir.resolve("second.env");
        Files.writeString(first, "DEEPSEEK_API_KEY=sk-first\nOPENAI_API_KEY=\n");
        Files.writeString(second, "export DEEPSEEK_API_KEY='sk-second'\n# comment\n");
        DotenvSecretStoreAdapter dotenvSecretStoreAdapter =
                new DotenvSecretStoreAdapter(dir.resolve("secrets.env"), new String[] {
                    first.toString(),
                    second.toString(),
                    dir.resolve("missing.env").toString()
                });

        assertThat(dotenvSecretStoreAdapter.readExternal())
                .containsExactly(org.assertj.core.api.Assertions.entry("DEEPSEEK_API_KEY", "sk-second"));
        dotenvSecretStoreAdapter.writeManaged(Map.of("X_API_KEY", "v"));
        assertThat(Files.readString(first)).isEqualTo("DEEPSEEK_API_KEY=sk-first\nOPENAI_API_KEY=\n");
    }
}
