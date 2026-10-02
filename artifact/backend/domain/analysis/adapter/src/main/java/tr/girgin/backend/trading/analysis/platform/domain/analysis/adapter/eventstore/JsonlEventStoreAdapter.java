package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.eventstore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.eventline.RunnerOutputLineParser;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.eventline.mapper.RunnerOutputLineToRunEventMapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.eventstore.mapper.RunEventTypeToStringMapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEvent;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.eventstore.EventStorePort;

/**
 * Reads the {@code events.jsonl} ta-runner writes into each run directory, and appends the few
 * events the platform produces itself, in the same format.
 */
@Component
class JsonlEventStoreAdapter implements EventStorePort {

    private static final int PROTOCOL_VERSION = 1;

    private final RunnerOutputLineToRunEventMapper eventMapper;
    private final RunEventTypeToStringMapper typeMapper;
    private final RunnerOutputLineParser parser = new RunnerOutputLineParser();
    private final JsonMapper json = JsonMapper.builder().build();
    private final Path runsDir;

    JsonlEventStoreAdapter(RunnerOutputLineToRunEventMapper eventMapper,
                           RunEventTypeToStringMapper typeMapper,
                           @Value("${platform.home}") Path platformHome) {
        this.eventMapper = eventMapper;
        this.typeMapper = typeMapper;
        this.runsDir = platformHome.resolve("runs");
    }

    @Override
    public List<RunEvent> read(AnalysisId id, long afterSeq) {
        Path file = eventsFile(id);
        if (!Files.exists(file)) {
            return List.of();
        }
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return lines.map(parser::parse)
                    .flatMap(Optional::stream)
                    .map(eventMapper::map)
                    .filter(event -> event.seq() > afterSeq)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read events of " + id, e);
        }
    }

    @Override
    public synchronized void append(AnalysisId id, RunEvent event) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("v", PROTOCOL_VERSION);
        line.put("ts", event.timestamp().toString());
        line.put("run_id", id.value());
        line.put("seq", event.seq());
        line.put("type", typeMapper.map(event.type()));
        line.putAll(event.payload());
        Path file = eventsFile(id);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, json.writeValueAsString(line) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not append an event for " + id, e);
        }
    }

    private Path eventsFile(AnalysisId id) {
        return runsDir.resolve(id.value()).resolve("events.jsonl");
    }
}
