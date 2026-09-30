package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.RunEventListener;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisFilter;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AssetType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Rating;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEvent;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEventType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunOutcome;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunStats;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.credentials.CredentialsPort;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.eventstore.EventStorePort;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.persistence.AnalysisRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runner.RunEventSink;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runner.RunHandle;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runner.RunnerPort;

/** In-memory stand-ins for the analysis domain's outbound ports. */
final class Fakes {

    private Fakes() {
    }

    static AnalysisSpec spec(String ticker) {
        return spec(ticker, LocalDate.of(2026, 9, 25));
    }

    static AnalysisSpec spec(String ticker, LocalDate tradeDate) {
        return new AnalysisSpec(ticker, tradeDate, AssetType.STOCK, List.of(Analyst.MARKET),
                "deepseek", "deepseek-v4-flash", "deepseek-v4-flash", 1, 1, "English", false);
    }

    static RunEvent event(long seq, RunEventType type) {
        return new RunEvent(seq, Instant.now(), type, null, null, null, null, Map.of());
    }

    static RunEvent stats(long seq, long llmCalls) {
        return new RunEvent(seq, Instant.now(), RunEventType.STATS, null, null,
                new RunStats(llmCalls, 1, 100, 50, null, Duration.ofSeconds(3)), null, Map.of());
    }

    static RunEvent decision(long seq, Rating rating) {
        return new RunEvent(seq, Instant.now(), RunEventType.DECISION, rating, null, null, null,
                Map.of("rating", rating.name(), "raw", "Rating: " + rating));
    }

    static RunEvent finished(long seq, RunOutcome outcome, String error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (error != null) {
            payload.put("error_type", "RuntimeError");
        }
        return new RunEvent(seq, Instant.now(), RunEventType.RUN_FINISHED, null, outcome, null, error, payload);
    }

    static final class Repository implements AnalysisRepositoryPort {

        final Map<AnalysisId, Analysis> rows = new ConcurrentHashMap<>();

        @Override
        public void insert(Analysis analysis) {
            rows.put(analysis.id(), analysis);
        }

        @Override
        public void update(Analysis analysis) {
            rows.put(analysis.id(), analysis);
        }

        @Override
        public void replaceImported(Analysis analysis) {
            rows.put(analysis.id(), analysis);
        }

        @Override
        public Optional<Analysis> findByExternalRef(String externalRef) {
            return rows.values().stream().filter(a -> externalRef.equals(a.externalRef())).findFirst();
        }

        @Override
        public Optional<Analysis> findById(AnalysisId id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public List<Analysis> findAll(AnalysisFilter filter) {
            return rows.values().stream()
                    .filter(a -> filter.status() == null || a.status() == filter.status())
                    .filter(a -> filter.ticker() == null || a.spec().ticker().equals(filter.ticker()))
                    .sorted(Comparator.comparing(Analysis::createdAt).reversed())
                    .toList();
        }

        @Override
        public List<Analysis> findByStatusIn(Collection<AnalysisStatus> statuses) {
            return rows.values().stream().filter(a -> statuses.contains(a.status())).toList();
        }
    }

    static final class Runner implements RunnerPort {

        final Map<AnalysisId, RunEventSink> sinks = new LinkedHashMap<>();
        final List<RunHandle> stopped = new ArrayList<>();
        // Runner refs whose runs are still going; reattach() finds only these.
        final Set<String> alive = new HashSet<>();
        final Map<AnalysisId, Long> reattachedAfter = new LinkedHashMap<>();
        Map<String, String> lastEnvironment;
        RuntimeException failOnStart;

        @Override
        public RunHandle start(AnalysisId id, AnalysisSpec spec, Map<String, String> environment, RunEventSink sink) {
            if (failOnStart != null) {
                throw failOnStart;
            }
            sinks.put(id, sink);
            lastEnvironment = environment;
            return new RunHandle("pid-" + id.value());
        }

        @Override
        public void stop(RunHandle handle) {
            stopped.add(handle);
        }

        @Override
        public boolean reattach(AnalysisId id, RunHandle handle, long afterSeq, RunEventSink sink) {
            if (!alive.contains(handle.ref())) {
                return false;
            }
            sinks.put(id, sink);
            reattachedAfter.put(id, afterSeq);
            return true;
        }

        RunEventSink sink(AnalysisId id) {
            return sinks.get(id);
        }
    }

    static final class EventStore implements EventStorePort {

        final Map<AnalysisId, List<RunEvent>> events = new ConcurrentHashMap<>();

        @Override
        public List<RunEvent> read(AnalysisId id, long afterSeq) {
            return events.getOrDefault(id, List.of()).stream().filter(e -> e.seq() > afterSeq).toList();
        }

        @Override
        public void append(AnalysisId id, RunEvent event) {
            events.computeIfAbsent(id, key -> new ArrayList<>()).add(event);
        }
    }

    static final class Credentials implements CredentialsPort {

        @Override
        public Map<String, String> environment() {
            return Map.of("DEEPSEEK_API_KEY", "sk-test");
        }
    }

    static class RecordingListener implements RunEventListener {

        final List<Long> seqs = new ArrayList<>();
        boolean completed;

        @Override
        public void onEvent(RunEvent event) {
            seqs.add(event.seq());
        }

        @Override
        public void onComplete() {
            completed = true;
        }
    }
}
