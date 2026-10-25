package tr.girgin.backend.trading.analysis.platform.orchestration.preview.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportSection;

class PreviewSettingsTest {

    private static PreviewSettings bind(Map<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bindOrCreate("platform.analysis.preview", PreviewSettings.class);
    }

    @Test
    void defaultsAreTheMarketReportAnd400Characters() {
        PreviewSettings settings = bind(Map.of());

        assertThat(settings.sections()).containsExactly(ReportSection.MARKET_REPORT);
        assertThat(settings.excerptLength()).isEqualTo(400);
    }

    @Test
    void sectionsAreReadLeniently() {
        PreviewSettings settings = bind(Map.of(
                "platform.analysis.preview.sections", "Market_Report, news_report",
                "platform.analysis.preview.excerpt-length", "5000"));

        assertThat(settings.sections())
                .containsExactlyInAnyOrder(ReportSection.MARKET_REPORT, ReportSection.NEWS_REPORT);
        assertThat(settings.excerptLength()).isEqualTo(5000);
    }

    @Test
    void anUnknownSectionFails() {
        assertThatThrownBy(() -> bind(Map.of("platform.analysis.preview.sections", "market_report,nonsense")))
                .isInstanceOf(BindException.class);
    }

    @Test
    void anExcerptLengthOutsideOneTo5000Fails() {
        assertThatThrownBy(() -> bind(Map.of("platform.analysis.preview.excerpt-length", "0")))
                .isInstanceOf(BindException.class);
        assertThatThrownBy(() -> bind(Map.of("platform.analysis.preview.excerpt-length", "5001")))
                .isInstanceOf(BindException.class);
    }
}
