package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.AnalysesApiDelegate;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.error.ApiException;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisReportDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisScopeEnumDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisStatusEnumDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.ChangeVisibilityRequest;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.PriceHistoryDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.RunLogDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.StartAnalysisRequest;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config.CurrentUser;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config.RequiresPermission;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config.SessionEventStreams;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.AnalysisScopeEnumDtoToAnalysisScopeMapper;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.AnalysisStatusEnumDtoToAnalysisStatusMapper;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.AnalysisViewToAnalysisDtoMapper;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.PriceHistoryToPriceHistoryDtoMapper;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.ReportToAnalysisReportDtoMapper;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.RunEventToRunEventDtoMapper;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.StartAnalysisRequestToRequestedAnalysisMapper;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.VisibilityEnumDtoToVisibilityMapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.ChangeAnalysisVisibilityPort;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.EventSubscription;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.GetAnalysisPort;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.ListAnalysesPort;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.ReadAnalysisLogsPort;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.RerunAnalysisPort;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.RunEventListener;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.StopAnalysisPort;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.SubscribeAnalysisEventsPort;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisAccess;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisFilter;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEvent;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.orchestration.analysisstart.inbound.StartAnalysisWithDefaultsUseCase;
import tr.girgin.backend.trading.analysis.platform.orchestration.analysisview.inbound.ViewAnalysesUseCase;
import tr.girgin.backend.trading.analysis.platform.orchestration.report.inbound.GetAnalysisPricesUseCase;
import tr.girgin.backend.trading.analysis.platform.orchestration.report.inbound.GetAnalysisReportUseCase;

@Service
// ClassFanOutComplexity: the analyses API's delegate, with a use case and a mapper per endpoint.
@SuppressWarnings("checkstyle:ClassFanOutComplexity")
class AnalysesApiDelegateImpl implements AnalysesApiDelegate {

    /** Sent once the run is over, so the browser closes the EventSource instead of reconnecting. */
    static final String END_EVENT = "end";

    private final StartAnalysisWithDefaultsUseCase startAnalysis;
    private final ListAnalysesPort listAnalyses;
    private final GetAnalysisPort getAnalysis;
    private final ChangeAnalysisVisibilityPort changeVisibility;
    private final StopAnalysisPort stopAnalysis;
    private final RerunAnalysisPort rerunAnalysis;
    private final SubscribeAnalysisEventsPort subscribeEvents;
    private final GetAnalysisReportUseCase getReport;
    private final GetAnalysisPricesUseCase getPrices;
    private final ReadAnalysisLogsPort readLogs;
    private final ViewAnalysesUseCase viewAnalyses;
    private final StartAnalysisRequestToRequestedAnalysisMapper requestMapper;
    private final AnalysisViewToAnalysisDtoMapper analysisMapper;
    private final RunEventToRunEventDtoMapper eventMapper;
    private final AnalysisStatusEnumDtoToAnalysisStatusMapper statusMapper;
    private final AnalysisScopeEnumDtoToAnalysisScopeMapper scopeMapper;
    private final VisibilityEnumDtoToVisibilityMapper visibilityMapper;
    private final ReportToAnalysisReportDtoMapper reportMapper;
    private final PriceHistoryToPriceHistoryDtoMapper pricesMapper;
    private final SessionEventStreams sessionEventStreams;
    private final CurrentUser currentUser;

    @SuppressWarnings("checkstyle:ParameterNumber") // constructor injection of the collaborators above
    AnalysesApiDelegateImpl(
            StartAnalysisWithDefaultsUseCase startAnalysis,
            ListAnalysesPort listAnalyses,
            GetAnalysisPort getAnalysis,
            ChangeAnalysisVisibilityPort changeVisibility,
            StopAnalysisPort stopAnalysis,
            RerunAnalysisPort rerunAnalysis,
            SubscribeAnalysisEventsPort subscribeEvents,
            GetAnalysisReportUseCase getReport,
            GetAnalysisPricesUseCase getPrices,
            ReadAnalysisLogsPort readLogs,
            ViewAnalysesUseCase viewAnalyses,
            StartAnalysisRequestToRequestedAnalysisMapper requestMapper,
            AnalysisViewToAnalysisDtoMapper analysisMapper,
            RunEventToRunEventDtoMapper eventMapper,
            AnalysisStatusEnumDtoToAnalysisStatusMapper statusMapper,
            AnalysisScopeEnumDtoToAnalysisScopeMapper scopeMapper,
            VisibilityEnumDtoToVisibilityMapper visibilityMapper,
            ReportToAnalysisReportDtoMapper reportMapper,
            PriceHistoryToPriceHistoryDtoMapper pricesMapper,
            SessionEventStreams sessionEventStreams,
            CurrentUser currentUser) {
        this.startAnalysis = startAnalysis;
        this.listAnalyses = listAnalyses;
        this.getAnalysis = getAnalysis;
        this.changeVisibility = changeVisibility;
        this.stopAnalysis = stopAnalysis;
        this.rerunAnalysis = rerunAnalysis;
        this.subscribeEvents = subscribeEvents;
        this.getReport = getReport;
        this.getPrices = getPrices;
        this.readLogs = readLogs;
        this.viewAnalyses = viewAnalyses;
        this.requestMapper = requestMapper;
        this.analysisMapper = analysisMapper;
        this.eventMapper = eventMapper;
        this.statusMapper = statusMapper;
        this.scopeMapper = scopeMapper;
        this.visibilityMapper = visibilityMapper;
        this.reportMapper = reportMapper;
        this.pricesMapper = pricesMapper;
        this.sessionEventStreams = sessionEventStreams;
        this.currentUser = currentUser;
    }

    @Override
    @RequiresPermission(Permission.ANALYSIS_RUN)
    public ResponseEntity<AnalysisDto> startAnalysis(StartAnalysisRequest request) {
        return created(startAnalysis.start(
                requestMapper.map(request), owner(), currentUser.has(Permission.ANALYSIS_OVERRIDE_MODELS)));
    }

    @Override
    @RequiresPermission(Permission.ANALYSIS_READ)
    public List<AnalysisDto> listAnalyses(AnalysisStatusEnumDto status, String ticker, AnalysisScopeEnumDto scope) {
        String normalizedTicker =
                ticker == null || ticker.isBlank() ? null : ticker.strip().toUpperCase(Locale.ROOT);
        AnalysisFilter filter = new AnalysisFilter(statusMapper.map(status), normalizedTicker, null, Set.of(), null);
        AnalysisAccess access = access();
        List<Analysis> analyses =
                listAnalyses.list(filter, scopeMapper.map(scope == null ? AnalysisScopeEnumDto.MINE : scope), access);
        return viewAnalyses.view(analyses, access).stream()
                .map(analysisMapper::map)
                .toList();
    }

    @Override
    @RequiresPermission(Permission.ANALYSIS_READ)
    public AnalysisDto getAnalysis(String id) {
        return dto(getAnalysis.get(new AnalysisId(id), access()));
    }

    @Override
    @RequiresPermission(Permission.ANALYSIS_READ)
    public AnalysisDto changeVisibility(String id, ChangeVisibilityRequest request) {
        return dto(changeVisibility.change(
                new AnalysisId(id), visibilityMapper.map(request.visibility()), access(), request.version()));
    }

    @Override
    @RequiresPermission(Permission.ANALYSIS_RUN)
    public AnalysisDto stopAnalysis(String id) {
        return dto(stopAnalysis.stop(new AnalysisId(id), access()));
    }

    @Override
    @RequiresPermission(Permission.ANALYSIS_RUN)
    public ResponseEntity<AnalysisDto> rerunAnalysis(String id) {
        return created(rerunAnalysis.rerun(new AnalysisId(id), access()));
    }

    @Override
    @RequiresPermission(Permission.ANALYSIS_READ)
    public AnalysisReportDto getReport(String id) {
        return getReport
                .getReport(new AnalysisId(id), access())
                .map(reportMapper::map)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "report_not_found",
                        "No report files for analysis " + id,
                        Map.of("id", id)));
    }

    @Override
    @RequiresPermission(Permission.ANALYSIS_READ)
    public PriceHistoryDto getPrices(String id) {
        return getPrices
                .getPrices(new AnalysisId(id), access())
                .map(pricesMapper::map)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "prices_not_found",
                        "No cached prices for analysis " + id,
                        Map.of("id", id)));
    }

    @Override
    @RequiresPermission(Permission.ANALYSIS_READ)
    public RunLogDto getLogs(String id, int tail) {
        return new RunLogDto(readLogs.tailLogs(new AnalysisId(id), access(), tail));
    }

    @Override
    @RequiresPermission(Permission.ANALYSIS_READ)
    public SseEmitter streamEvents(String id, long afterSeq, HttpServletRequest http) {
        // No timeout: runs take minutes, and the stream ends with the run.
        SseEmitter emitter = new SseEmitter(0L);
        AtomicReference<EventSubscription> subscription = new AtomicReference<>();
        AtomicBoolean ended = new AtomicBoolean();
        Runnable cancel = () -> {
            EventSubscription current = subscription.get();
            if (current != null) {
                current.cancel();
            }
        };
        emitter.onCompletion(cancel);
        emitter.onTimeout(cancel);
        emitter.onError(_ -> cancel.run());
        // The headers only go out with the first write: a comment commits the stream at once, so the
        // client sees it open while the run has yet to send an event (and can be closed meanwhile).
        send(emitter, SseEmitter.event().comment("open"));
        // Events sent before this method returns are buffered by the emitter.
        subscription.set(subscribeEvents.subscribe(new AnalysisId(id), afterSeq, access(), new RunEventListener() {
            @Override
            public void onEvent(RunEvent event) {
                send(
                        emitter,
                        SseEmitter.event()
                                .id(Long.toString(event.seq()))
                                .data(eventMapper.map(event), MediaType.APPLICATION_JSON));
            }

            @Override
            public void onComplete() {
                send(emitter, SseEmitter.event().name(END_EVENT).data("{}", MediaType.APPLICATION_JSON));
                ended.set(true);
                emitter.complete();
            }
        }));
        // The stream belongs to the session that opened it and ends with it. It is registered only
        // once the subscription holds: a failed one never returns the emitter to Spring MVC, so
        // nothing would ever remove it again. The security chain guarantees a session here; without
        // one (not reachable in practice) nothing is registered.
        HttpSession session = http.getSession(false);
        if (session != null) {
            sessionEventStreams.register(session.getId(), emitter);
            // A run that ended before Spring MVC initialised the emitter (a finished run replays and
            // completes inside subscribe) never runs the removal callbacks, so remove it here.
            if (ended.get()) {
                sessionEventStreams.unregister(emitter);
            }
        }
        return emitter;
    }

    private OwnerId owner() {
        return new OwnerId(currentUser.id().value());
    }

    private AnalysisAccess access() {
        return new AnalysisAccess(
                owner(), currentUser.has(Permission.ANALYSIS_READ_ALL), currentUser.has(Permission.ANALYSIS_SHARE));
    }

    private ResponseEntity<AnalysisDto> created(Analysis analysis) {
        return ResponseEntity.created(
                        URI.create("/api/analyses/" + analysis.id().value()))
                .body(dto(analysis));
    }

    private AnalysisDto dto(Analysis analysis) {
        return analysisMapper.map(viewAnalyses.view(analysis, access()));
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
