package tr.girgin.backend.trading.analysis.platform.orchestration.preview.inbound;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportSection;

@ConfigurationProperties("platform.analysis.preview")
public record PreviewSettings(
        @DefaultValue("market_report") Set<ReportSection> sections,
        @DefaultValue("400") int excerptLength) {

    static final int LOWEST_EXCERPT_LENGTH = 1;
    static final int HIGHEST_EXCERPT_LENGTH = 5000;

    public PreviewSettings {
        sections = Set.copyOf(sections);
        if (excerptLength < LOWEST_EXCERPT_LENGTH || excerptLength > HIGHEST_EXCERPT_LENGTH) {
            throw new IllegalArgumentException("platform.analysis.preview.excerpt-length must be between "
                    + LOWEST_EXCERPT_LENGTH + " and " + HIGHEST_EXCERPT_LENGTH + " but is " + excerptLength);
        }
    }
}
