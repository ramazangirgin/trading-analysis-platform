package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.credentials;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnvFileCredentialsAdapterTest {

    @TempDir
    Path dir;

    @Test
    void parsesDotenvSyntax() throws Exception {
        Path file = dir.resolve(".env");
        Files.writeString(file, """
                # provider keys
                OPENAI_API_KEY=
                DEEPSEEK_API_KEY=sk-deep
                export ANTHROPIC_API_KEY="sk-ant with space"
                GOOGLE_API_KEY='sk-goog'
                XAI_API_KEY=sk-xai # trailing comment
                   TRADINGAGENTS_LLM_PROVIDER = deepseek
                not a variable line
                """);

        assertThat(new EnvFileCredentialsAdapter(file).environment()).containsExactly(
                org.assertj.core.api.Assertions.entry("DEEPSEEK_API_KEY", "sk-deep"),
                org.assertj.core.api.Assertions.entry("ANTHROPIC_API_KEY", "sk-ant with space"),
                org.assertj.core.api.Assertions.entry("GOOGLE_API_KEY", "sk-goog"),
                org.assertj.core.api.Assertions.entry("XAI_API_KEY", "sk-xai"),
                org.assertj.core.api.Assertions.entry("TRADINGAGENTS_LLM_PROVIDER", "deepseek"));
    }

    @Test
    void aMissingFileMeansNoKeys() {
        assertThat(new EnvFileCredentialsAdapter(dir.resolve("absent.env")).environment()).isEmpty();
    }
}
