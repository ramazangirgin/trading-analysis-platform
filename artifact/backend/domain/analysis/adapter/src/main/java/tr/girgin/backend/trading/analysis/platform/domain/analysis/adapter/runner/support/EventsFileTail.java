package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.runner.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the lines appended to a file since the last call, like {@code tail -f}. A line still being
 * written (no newline yet) is held back until it is complete.
 */
public final class EventsFileTail {

    private static final int CHUNK_BYTES = 64 * 1024;

    private final Path file;
    private final ByteArrayOutputStream partial = new ByteArrayOutputStream();
    private long position;

    public EventsFileTail(Path file) {
        this.file = file;
    }

    public List<String> readNewLines() throws IOException {
        List<String> lines = new ArrayList<>();
        if (!Files.exists(file)) {
            return lines;
        }
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(CHUNK_BYTES);
            int read;
            while ((read = channel.read(buffer, position)) > 0) {
                position += read;
                for (int i = 0; i < read; i++) {
                    byte b = buffer.get(i);
                    if (b == '\n') {
                        lines.add(partial.toString(StandardCharsets.UTF_8));
                        partial.reset();
                    } else {
                        partial.write(b);
                    }
                }
                buffer.clear();
            }
        }
        return lines;
    }
}
