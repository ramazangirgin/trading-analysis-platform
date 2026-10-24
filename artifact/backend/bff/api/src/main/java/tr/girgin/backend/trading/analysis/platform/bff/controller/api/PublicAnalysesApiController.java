package tr.girgin.backend.trading.analysis.platform.bff.controller.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisPreviewDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisPreviewPageDto;

/** The routes open to visitors without a session: the preview of the partial analyses. */
@RestController
@RequestMapping("/api/public/analyses")
public class PublicAnalysesApiController {

    private final PublicAnalysesApiDelegate delegate;

    public PublicAnalysesApiController(PublicAnalysesApiDelegate delegate) {
        this.delegate = delegate;
    }

    /** The partial analyses, newest first. */
    @GetMapping
    public AnalysisPreviewPageDto listPublicAnalyses(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(24) int size) {
        return delegate.listPublicAnalyses(page, size);
    }

    /** The preview of one partial analysis; 404 for any other. */
    @GetMapping("/{id}")
    public AnalysisPreviewDto getPublicAnalysis(@PathVariable String id) {
        return delegate.getPublicAnalysis(id);
    }
}
