package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisError;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.eventstore.EventStorePort;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.persistence.AnalysisRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runner.RunnerPort;

class AnalysisServiceInvalidIdTest {

    @Test
    void anInvalidIdIsNotFoundWithoutAPortCall() {
        AnalysisRepositoryPort repository = Fakes.untouched(AnalysisRepositoryPort.class);
        EventStorePort eventStore = Fakes.untouched(EventStorePort.class);
        AnalysisService analysisService = Fakes.analysisService(
                repository,
                Fakes.untouched(RunnerPort.class),
                eventStore,
                Fakes.events(repository, eventStore),
                Clock.fixed(Instant.parse("2026-10-01T05:06:07Z"), ZoneOffset.UTC),
                2);
        AnalysisId id = new AnalysisId("../x");

        assertNotFound(() -> analysisService.get(id, Fakes.OWN));
        assertNotFound(() -> analysisService.stop(id, Fakes.OWN));
        assertNotFound(() -> analysisService.rerun(id, Fakes.OWN));
    }

    private void assertNotFound(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        AnalysisException.class, e -> assertThat(e.error()).isEqualTo(AnalysisError.NOT_FOUND));
    }
}
