package tr.girgin.backend.trading.analysis.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AssetType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Rating;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Visibility;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.persistence.AnalysisRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.library.persistence.TestDatabaseConfiguration;

/** The preview settings and the rate limit of the open routes, in a context with other values. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestDatabaseConfiguration.class)
// Closes the context (and its database container) when the class is done: the container engine of a laptop
// does not keep one more PostgreSQL alive for the rest of the build.
@DirtiesContext
@TestPropertySource(
        properties = {
            "platform.analysis.preview.sections=market_report,news_report",
            "platform.public.rate-limit.max-requests=6",
            "platform.public.rate-limit.window=1h"
        })
class PartialPreviewConfigurationIntegrationTest {

    private static final Path HOME = TestPlatformHome.create();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Autowired
    private AnalysisRepositoryPort analyses;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestPlatformHome.register(registry, HOME, HOME.resolve("runner.sh"));
    }

    @Test
    void theConfiguredSectionsDecideWhatAVisitorReadsAndTheRequestsOverTheLimitAreRefused() throws Exception {
        AnalysisId id = AnalysisId.newId();
        analyses.insert(Analysis.queued(
                id,
                new AnalysisSpec(
                        "CFGA",
                        LocalDate.of(2026, 9, 25),
                        AssetType.STOCK,
                        List.of(Analyst.MARKET),
                        "deepseek",
                        "deepseek-v4-pro",
                        "deepseek-v4-flash",
                        1,
                        1,
                        "English",
                        true),
                new OwnerId("00000000-0000-0000-0000-000000000001"),
                Instant.now()));
        analyses.update(analyses.findById(id)
                .orElseThrow()
                .running(Instant.now(), "ref")
                .withDecision(Rating.HOLD, "Hold")
                .finished(AnalysisStatus.COMPLETED, Instant.now(), null, null));
        analyses.update(analyses.findById(id).orElseThrow().withVisibility(Visibility.PARTIAL, Instant.now()));
        writeReport();

        HttpResponse<String> preview = get("/api/public/analyses/" + id.value());

        assertThat(preview.statusCode()).isEqualTo(200);
        JsonNode body = JSON.readTree(preview.body());
        assertThat(body.at("/sections/market_report").asString()).isEqualTo("market text");
        assertThat(body.at("/sections/news_report").asString()).isEqualTo("news text");
        assertThat(body.get("lockedSections").valueStream().map(JsonNode::asString))
                .containsExactly("sentiment_report")
                .doesNotContain("news_report");

        // The preview was the first request of the six allowed.
        for (int i = 0; i < 5; i++) {
            assertThat(get("/api/public/analyses").statusCode()).isEqualTo(200);
        }
        HttpResponse<String> refused = get("/api/public/analyses");
        assertThat(refused.statusCode()).isEqualTo(429);
        assertThat(refused.headers().firstValue("Retry-After")).isPresent();
        assertThat(JSON.readTree(refused.body()).get("errorCode").asString()).isEqualTo("too_many_requests");
    }

    private static void writeReport() throws IOException {
        Path analysts = Files.createDirectories(HOME.resolve("logs/CFGA/2026-09-25/reports/1_analysts"));
        Files.writeString(analysts.resolve("market.md"), "market text");
        Files.writeString(analysts.resolve("news.md"), "news text");
        Files.writeString(analysts.resolve("sentiment.md"), "sentiment text");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
    }
}
