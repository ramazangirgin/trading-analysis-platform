package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.secrets;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.outbound.secrets.SecretStorePort;

/**
 * The platform's secrets file (owner read/write only, replaced atomically) and read-only dotenv
 * files next to it. Values are never logged.
 */
@Component
class DotenvSecretStoreAdapter implements SecretStorePort {

    private static final Pattern LINE = Pattern.compile("^\\s*(?:export\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.*?)\\s*$");
    private static final String HEADER = "# Managed by TradingAgents Platform (Settings). Edits here are kept.\n";

    private final Path managedFile;
    private final List<Path> externalFiles;

    DotenvSecretStoreAdapter(@Value("${platform.secrets.env-file}") Path managedFile,
                             @Value("${platform.secrets.external-env-files:}") String[] externalFiles) {
        this.managedFile = managedFile;
        this.externalFiles = java.util.Arrays.stream(externalFiles)
                .filter(name -> name != null && !name.isBlank())
                .map(Path::of)
                .toList();
    }

    @Override
    public Map<String, String> readManaged() {
        return read(managedFile);
    }

    @Override
    public Map<String, String> readExternal() {
        Map<String, String> merged = new LinkedHashMap<>();
        externalFiles.forEach(file -> merged.putAll(read(file)));
        return merged;
    }

    @Override
    public synchronized void writeManaged(Map<String, String> secrets) {
        StringBuilder content = new StringBuilder(HEADER);
        secrets.forEach((name, value) -> content.append(name).append("=\"")
                .append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"\n"));
        try {
            Path dir = managedFile.toAbsolutePath().getParent();
            Files.createDirectories(dir);
            Path temp = Files.createTempFile(dir, ".secrets", ".tmp", PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rw-------")));
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            try {
                Files.move(temp, managedFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, managedFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the secrets file", e);
        }
    }

    static Map<String, String> read(Path file) {
        Map<String, String> variables = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) {
            return variables;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                Matcher matcher = LINE.matcher(line);
                if (!line.stripLeading().startsWith("#") && matcher.matches()) {
                    String value = unquote(matcher.group(2));
                    if (!value.isEmpty()) {
                        variables.put(matcher.group(1), value);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
        return variables;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1);
        }
        int comment = value.indexOf(" #");
        return comment >= 0 ? value.substring(0, comment).strip() : value;
    }
}
