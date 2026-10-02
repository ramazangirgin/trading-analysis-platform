package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.credentials;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnvFileCredentialsAdapterTest {

    @TempDir
    private Path dir;

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

        assertThat(new EnvFileCredentialsAdapter(file, new String[0]).environment())
                .containsExactly(
                        org.assertj.core.api.Assertions.entry("DEEPSEEK_API_KEY", "sk-deep"),
                        org.assertj.core.api.Assertions.entry("ANTHROPIC_API_KEY", "sk-ant with space"),
                        org.assertj.core.api.Assertions.entry("GOOGLE_API_KEY", "sk-goog"),
                        org.assertj.core.api.Assertions.entry("XAI_API_KEY", "sk-xai"),
                        org.assertj.core.api.Assertions.entry("TRADINGAGENTS_LLM_PROVIDER", "deepseek"));
    }

    @Test
    void thePlatformFileWinsAndProcessVariablesAreNeverPassedOn() throws Exception {
        Path external = dir.resolve("upstream.env");
        Path platform = dir.resolve("secrets.env");
        Files.writeString(external, "DEEPSEEK_API_KEY=sk-upstream\nOPENAI_API_KEY=sk-openai\nPATH=/evil\n");
        Files.writeString(
                platform, "DEEPSEEK_API_KEY=\"sk-platform\"\nDYLD_INSERT_LIBRARIES=/evil.dylib\nPYTHONPATH=/evil\n");

        EnvFileCredentialsAdapter adapter = new EnvFileCredentialsAdapter(platform, new String[] {external.toString()});
        assertThat(adapter.environment())
                .containsExactly(
                        org.assertj.core.api.Assertions.entry("DEEPSEEK_API_KEY", "sk-platform"),
                        org.assertj.core.api.Assertions.entry("OPENAI_API_KEY", "sk-openai"));
    }

    @Test
    void aMissingFileMeansNoKeys() {
        assertThat(new EnvFileCredentialsAdapter(dir.resolve("absent.env"), new String[0]).environment())
                .isEmpty();
    }
}
