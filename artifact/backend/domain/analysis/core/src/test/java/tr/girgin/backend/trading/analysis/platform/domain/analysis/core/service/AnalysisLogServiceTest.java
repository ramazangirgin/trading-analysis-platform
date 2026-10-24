package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisError;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Visibility;

class AnalysisLogServiceTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Incorrect API key provided: sk-proj-abcdEFGH12345678xyz|Incorrect API key provided: sk-proj-abcd****",
                "key=sk-1234567890abcdef|key=sk-1234****",
                "google AIzaSyA1234567890123456|google AIzaSyA1****",
                "nothing secret here, sk- alone|nothing secret here, sk- alone"
            })
    void masksApiKeys(String line, String expected) {
        assertThat(new AnalysisLogService(null, null).mask(line)).isEqualTo(expected);
    }

    @Test
    void anotherUsersLogIsNotFoundUnlessTheCallerSeesAllUsers() {
        Fakes.Repository repository = new Fakes.Repository();
        Fakes.EventStore eventStore = new Fakes.EventStore();
        AnalysisService analyses = new AnalysisService(
                repository,
                new Fakes.Runner(),
                new Fakes.Credentials(),
                eventStore,
                new AnalysisEventHub(repository, eventStore),
                Clock.fixed(Instant.parse("2026-10-01T05:06:07Z"), ZoneOffset.UTC),
                2);
        AnalysisLogService service = new AnalysisLogService(analyses, (_, _) -> List.of("key=sk-1234567890abcdef"));
        Analysis analysis = analyses.start(Fakes.spec("NVDA"), Fakes.OWNER);
        AnalysisId id = analysis.id();

        assertThatThrownBy(() -> service.tailLogs(id, Fakes.OTHERS, 10))
                .isInstanceOfSatisfying(
                        AnalysisException.class, e -> assertThat(e.error()).isEqualTo(AnalysisError.NOT_FOUND));
        assertThat(service.tailLogs(id, Fakes.OWN, 10)).containsExactly("key=sk-1234****");
        assertThat(service.tailLogs(id, Fakes.EVERYONE, 10)).containsExactly("key=sk-1234****");
    }

    @ParameterizedTest
    @EnumSource(
            value = Visibility.class,
            names = {"PUBLIC", "PARTIAL"})
    void anotherUsersSharedAnalysisHasNoLogForTheCaller(Visibility visibility) {
        Fakes.Repository repository = new Fakes.Repository();
        Fakes.EventStore eventStore = new Fakes.EventStore();
        AnalysisService analyses = new AnalysisService(
                repository,
                new Fakes.Runner(),
                new Fakes.Credentials(),
                eventStore,
                new AnalysisEventHub(repository, eventStore),
                Clock.fixed(Instant.parse("2026-10-01T05:06:07Z"), ZoneOffset.UTC),
                2);
        AnalysisLogService service = new AnalysisLogService(analyses, (_, _) -> List.of("line"));
        AnalysisId id = analyses.start(Fakes.spec("NVDA"), Fakes.OWNER).id();
        repository.rows.put(id, repository.rows.get(id).withVisibility(visibility, Instant.now()));

        assertThatThrownBy(() -> service.tailLogs(id, Fakes.OTHERS, 10))
                .isInstanceOfSatisfying(
                        AnalysisException.class, e -> assertThat(e.error()).isEqualTo(AnalysisError.NOT_FOUND));
        assertThat(service.tailLogs(id, Fakes.OWN, 10)).containsExactly("line");
        assertThat(service.tailLogs(id, Fakes.EVERYONE, 10)).containsExactly("line");
    }
}
