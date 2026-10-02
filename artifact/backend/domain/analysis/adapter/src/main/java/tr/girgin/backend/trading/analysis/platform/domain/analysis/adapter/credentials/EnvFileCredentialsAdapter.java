package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.credentials;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.credentials.CredentialsPort;

/**
 * Builds the runner's extra environment from dotenv files on every run start, so edits apply
 * without a restart: the read-only files first, then the platform's secrets file, which wins.
 * Variables that would change how the runner process itself starts are never passed on.
 * Values are never logged; only the variable names are.
 */
@Component
class EnvFileCredentialsAdapter implements CredentialsPort {

    private static final Logger log = LoggerFactory.getLogger(EnvFileCredentialsAdapter.class);
    private static final Pattern LINE =
            Pattern.compile("^\\s*(?:export\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.*?)\\s*$");

    private static final Set<String> BLOCKED = Set.of("PATH", "HOME", "USER", "SHELL", "TMPDIR", "VIRTUAL_ENV");
    private static final List<String> BLOCKED_PREFIXES = List.of("LD_", "DYLD_", "PYTHON", "JAVA_", "_JAVA");

    private final List<Path> files;

    EnvFileCredentialsAdapter(
            @Value("${platform.secrets.env-file}") Path envFile,
            @Value("${platform.secrets.external-env-files:}") String[] externalFiles) {
        List<Path> ordered = new java.util.ArrayList<>(java.util.Arrays.stream(externalFiles)
                .filter(name -> name != null && !name.isBlank())
                .map(Path::of)
                .toList());
        ordered.add(envFile);
        this.files = List.copyOf(ordered);
    }

    @Override
    public Map<String, String> environment() {
        Map<String, String> variables = new LinkedHashMap<>();
        files.forEach(file -> variables.putAll(read(file)));
        variables.keySet().removeIf(EnvFileCredentialsAdapter::blocked);
        if (variables.isEmpty()) {
            log.warn("No provider keys found in {}; runs get none", files);
        }
        return variables;
    }

    private static boolean blocked(String name) {
        return BLOCKED.contains(name) || BLOCKED_PREFIXES.stream().anyMatch(name::startsWith);
    }

    private static Map<String, String> read(Path envFile) {
        if (!Files.isRegularFile(envFile)) {
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
            return variables;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + envFile, e);
        }
    }

    private static String unquote(String value) {
        if (value.length() >= 2
                && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        int comment = value.indexOf(" #");
        return comment >= 0 ? value.substring(0, comment).strip() : value;
    }
}
