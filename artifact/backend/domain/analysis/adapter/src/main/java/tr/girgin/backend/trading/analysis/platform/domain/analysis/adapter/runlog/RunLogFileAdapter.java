package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.runlog;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runlog.RunLogPort;

/** Reads the end of {@code <run dir>/run.log} (the runner's stderr) without loading the whole file. */
@Component
class RunLogFileAdapter implements RunLogPort {

    private static final int MAX_BYTES = 2 * 1024 * 1024;

    private final Path runsDir;

    RunLogFileAdapter(@Value("${platform.home}") Path platformHome) {
        this.runsDir = platformHome.resolve("runs");
    }

    @Override
    public List<String> tail(AnalysisId id, int maxLines) {
        Path file = runsDir.resolve(id.value()).resolve("run.log");
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long length = raf.length();
            int toRead = (int) Math.min(length, MAX_BYTES);
            byte[] bytes = new byte[toRead];
            raf.seek(length - toRead);
            raf.readFully(bytes);
            List<String> lines = Arrays.asList(new String(bytes, StandardCharsets.UTF_8).split("\\R", -1));
            if (toRead < length && !lines.isEmpty()) {
                lines = lines.subList(1, lines.size()); // the first line was cut
            }
            if (!lines.isEmpty() && lines.getLast().isEmpty()) {
                lines = lines.subList(0, lines.size() - 1);
            }
            return List.copyOf(lines.subList(Math.max(0, lines.size() - maxLines), lines.size()));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the log of " + id, e);
        }
    }
}
