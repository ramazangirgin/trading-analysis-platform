package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.AnalysesApiDelegate;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisStatusDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.StartAnalysisRequest;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.EventSubscription;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.GetAnalysisUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.ListAnalysesUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.RerunAnalysisUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.RunEventListener;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.StartAnalysisUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.StopAnalysisUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.SubscribeAnalysisEventsUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisFilter;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEvent;

@Service
class AnalysesApiDelegateImpl implements AnalysesApiDelegate {

    /** Sent once the run is over, so the browser closes the EventSource instead of reconnecting. */
    static final String END_EVENT = "end";

    private final StartAnalysisUseCase startAnalysis;
    private final ListAnalysesUseCase listAnalyses;
    private final GetAnalysisUseCase getAnalysis;
    private final StopAnalysisUseCase stopAnalysis;
    private final RerunAnalysisUseCase rerunAnalysis;
    private final SubscribeAnalysisEventsUseCase subscribeEvents;
    private final StartAnalysisRequestToAnalysisSpecMapper specMapper;
    private final AnalysisToAnalysisDtoMapper analysisMapper;
    private final RunEventToRunEventDtoMapper eventMapper;
    private final StringToAnalysisIdMapper idMapper;
    private final AnalysisStatusDtoToAnalysisStatusMapper statusMapper;
    private final AnalysisExceptionToApiExceptionMapper errorMapper;

    AnalysesApiDelegateImpl(StartAnalysisUseCase startAnalysis,
                            ListAnalysesUseCase listAnalyses,
                            GetAnalysisUseCase getAnalysis,
                            StopAnalysisUseCase stopAnalysis,
                            RerunAnalysisUseCase rerunAnalysis,
                            SubscribeAnalysisEventsUseCase subscribeEvents,
                            StartAnalysisRequestToAnalysisSpecMapper specMapper,
                            AnalysisToAnalysisDtoMapper analysisMapper,
                            RunEventToRunEventDtoMapper eventMapper,
                            StringToAnalysisIdMapper idMapper,
                            AnalysisStatusDtoToAnalysisStatusMapper statusMapper,
                            AnalysisExceptionToApiExceptionMapper errorMapper) {
        this.startAnalysis = startAnalysis;
        this.listAnalyses = listAnalyses;
        this.getAnalysis = getAnalysis;
        this.stopAnalysis = stopAnalysis;
        this.rerunAnalysis = rerunAnalysis;
        this.subscribeEvents = subscribeEvents;
        this.specMapper = specMapper;
        this.analysisMapper = analysisMapper;
        this.eventMapper = eventMapper;
        this.idMapper = idMapper;
        this.statusMapper = statusMapper;
        this.errorMapper = errorMapper;
    }

    @Override
    public ResponseEntity<AnalysisDto> startAnalysis(StartAnalysisRequest request) {
        return created(call(() -> startAnalysis.start(specMapper.map(request))));
    }

    @Override
    public ResponseEntity<List<AnalysisDto>> listAnalyses(AnalysisStatusDto status, String ticker) {
        String normalizedTicker = ticker == null || ticker.isBlank() ? null : ticker.strip().toUpperCase(Locale.ROOT);
        AnalysisFilter filter = new AnalysisFilter(statusMapper.map(status), normalizedTicker);
        return ResponseEntity.ok(listAnalyses.list(filter).stream().map(analysisMapper::map).toList());
    }

    @Override
    public ResponseEntity<AnalysisDto> getAnalysis(String id) {
        return ResponseEntity.ok(analysisMapper.map(call(() -> getAnalysis.get(idMapper.map(id)))));
    }

    @Override
    public ResponseEntity<AnalysisDto> stopAnalysis(String id) {
        return ResponseEntity.ok(analysisMapper.map(call(() -> stopAnalysis.stop(idMapper.map(id)))));
    }

    @Override
    public ResponseEntity<AnalysisDto> rerunAnalysis(String id) {
        return created(call(() -> rerunAnalysis.rerun(idMapper.map(id))));
    }

    @Override
    public SseEmitter streamEvents(String id, long afterSeq) {
        // No timeout: runs take minutes, and the stream ends with the run.
        SseEmitter emitter = new SseEmitter(0L);
        AtomicReference<EventSubscription> subscription = new AtomicReference<>();
        Runnable cancel = () -> {
            EventSubscription current = subscription.get();
            if (current != null) {
                current.cancel();
            }
        };
        emitter.onCompletion(cancel);
        emitter.onTimeout(cancel);
        emitter.onError(error -> cancel.run());
        // Events sent before this method returns are buffered by the emitter.
        subscription.set(call(() -> subscribeEvents.subscribe(idMapper.map(id), afterSeq, new RunEventListener() {
            @Override
            public void onEvent(RunEvent event) {
                send(emitter, SseEmitter.event()
                        .id(Long.toString(event.seq()))
                        .data(eventMapper.map(event), MediaType.APPLICATION_JSON));
            }

            @Override
            public void onComplete() {
                send(emitter, SseEmitter.event().name(END_EVENT).data("{}", MediaType.APPLICATION_JSON));
                emitter.complete();
            }
        })));
        return emitter;
    }

    private ResponseEntity<AnalysisDto> created(Analysis analysis) {
        return ResponseEntity.created(URI.create("/api/analyses/" + analysis.id().value()))
                .body(analysisMapper.map(analysis));
    }

    private <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (AnalysisException e) {
            throw errorMapper.map(e);
        }
    }

    private static void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
