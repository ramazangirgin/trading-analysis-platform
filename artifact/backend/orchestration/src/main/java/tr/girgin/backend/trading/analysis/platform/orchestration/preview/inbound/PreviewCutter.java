package tr.girgin.backend.trading.analysis.platform.orchestration.preview.inbound;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound.GetReportUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.Report;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportSection;
import tr.girgin.backend.trading.analysis.platform.orchestration.preview.model.AnalysisPreview;
import tr.girgin.backend.trading.analysis.platform.orchestration.preview.model.AnalysisPreviewSummary;

/** The one place that decides which parts of a report a visitor gets: everything else is cut here. */
@Component
class PreviewCutter {

    private static final String ELLIPSIS = "…";

    private final GetReportUseCase getReport;
    private final PreviewSettings settings;

    PreviewCutter(GetReportUseCase getReport, PreviewSettings settings) {
        this.getReport = getReport;
        this.settings = settings;
    }

    AnalysisPreview preview(Analysis analysis) {
        Optional<Report> report = report(analysis);
        Map<ReportSection, String> allowed = new EnumMap<>(ReportSection.class);
        List<String> locked = new ArrayList<>();
        report.ifPresent(r -> {
            r.sections().forEach((section, text) -> {
                if (settings.sections().contains(section)) {
                    allowed.put(section, text);
                } else {
                    locked.add(key(section));
                }
            });
            r.debates().keySet().forEach(speaker -> locked.add(key(speaker)));
        });
        return new AnalysisPreview(
                analysis.id(),
                analysis.spec().ticker(),
                analysis.spec().tradeDate(),
                analysis.rating(),
                allowed,
                locked);
    }

    AnalysisPreviewSummary summary(Analysis analysis) {
        String excerpt = report(analysis)
                .flatMap(r -> r.sections().entrySet().stream()
                        .filter(e -> settings.sections().contains(e.getKey())
                                && !e.getValue().isBlank())
                        .findFirst())
                .map(e -> excerpt(e.getValue()))
                .orElse(null);
        return new AnalysisPreviewSummary(
                analysis.id(), analysis.spec().ticker(), analysis.spec().tradeDate(), analysis.rating(), excerpt);
    }

    private Optional<Report> report(Analysis analysis) {
        return ReportKey.isValidTicker(analysis.spec().ticker())
                ? getReport.get(
                        new ReportKey(analysis.spec().ticker(), analysis.spec().tradeDate()))
                : Optional.empty();
    }

    private String excerpt(String text) {
        String trimmed = text.strip();
        int length = settings.excerptLength();
        if (trimmed.length() <= length) {
            return trimmed;
        }
        int end = length > 0 && Character.isHighSurrogate(trimmed.charAt(length - 1)) ? length - 1 : length;
        String cut = trimmed.substring(0, end);
        int lineBreak = cut.lastIndexOf('\n');
        if (lineBreak > 0) {
            cut = cut.substring(0, lineBreak);
        }
        return cut.stripTrailing() + ELLIPSIS;
    }

    private String key(Enum<?> part) {
        return part.name().toLowerCase(Locale.ROOT);
    }
}
