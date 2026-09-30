package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.runner;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EventsFileTailTest {

    @TempDir
    Path dir;

    @Test
    void returnsOnlyNewCompleteLines() throws Exception {
        Path file = dir.resolve("events.jsonl");
        EventsFileTail tail = new EventsFileTail(file);

        assertThat(tail.readNewLines()).isEmpty();

        Files.writeString(file, "one\ntwo\nthr");
        assertThat(tail.readNewLines()).containsExactly("one", "two");

        Files.writeString(file, "ee — üç\n", StandardOpenOption.APPEND);
        assertThat(tail.readNewLines()).containsExactly("three — üç");
        assertThat(tail.readNewLines()).isEmpty();
    }
}
