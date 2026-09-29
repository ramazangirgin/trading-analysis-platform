package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.credentials;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.credentials.CredentialsPort;

/**
 * Reads provider keys from a dotenv file on every run start, so edits apply without a restart.
 * Values are never logged; only the variable names are.
 */
@Component
class EnvFileCredentialsAdapter implements CredentialsPort {

    private static final Logger log = LoggerFactory.getLogger(EnvFileCredentialsAdapter.class);
    private static final Pattern LINE = Pattern.compile("^\\s*(?:export\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.*?)\\s*$");

    private final Path envFile;

    EnvFileCredentialsAdapter(@Value("${platform.secrets.env-file}") Path envFile) {
        this.envFile = envFile;
    }

    @Override
    public Map<String, String> environment() {
        if (!Files.isRegularFile(envFile)) {
            log.warn("Secrets file {} not found; runs get no provider keys", envFile.toAbsolutePath());
            return Map.of();
        }
        try {
            Map<String, String> variables = new LinkedHashMap<>();
            for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                Matcher matcher = LINE.matcher(line);
                if (!line.stripLeading().startsWith("#") && matcher.matches()) {
                    String value = unquote(matcher.group(2));
                    if (!value.isEmpty()) {
                        variables.put(matcher.group(1), value);
                    }
                }
            }
            log.debug("Loaded {} from {}", variables.keySet(), envFile);
            return variables;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + envFile, e);
        }
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                || value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        int comment = value.indexOf(" #");
        return comment >= 0 ? value.substring(0, comment).strip() : value;
    }
}
