package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisError;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.PartialAnalysisPage;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Visibility;

class PartialAnalysisServiceTest {

    private static final Instant BASE = Instant.parse("2026-10-01T05:06:07Z");

    private Fakes.Repository repository;
    private PartialAnalysisService service;
    private int created;

    @BeforeEach
    void setUp() {
        repository = new Fakes.Repository();
        service = new PartialAnalysisService(repository);
    }

    private Analysis store(Visibility visibility, AnalysisStatus status) {
        Analysis queued =
                Analysis.queued(AnalysisId.newId(), Fakes.spec("NVDA"), Fakes.OWNER, BASE.plusSeconds(created++));
        Analysis stored = status == AnalysisStatus.QUEUED ? queued : queued.finished(status, BASE, null, null);
        Analysis shared = stored.withVisibility(visibility, BASE);
        repository.insert(shared);
        return shared;
    }

    @Test
    void getAnswersAPartialAnalysis() {
        Analysis partial = store(Visibility.PARTIAL, AnalysisStatus.COMPLETED);

        assertThat(service.get(partial.id())).isEqualTo(partial);
    }

    @Test
    void getDoesNotRevealAPrivateOrPublicAnalysisOrAnUnknownOne() {
        Analysis hidden = store(Visibility.PRIVATE, AnalysisStatus.COMPLETED);
        Analysis shared = store(Visibility.PUBLIC, AnalysisStatus.COMPLETED);

        assertNotFound(hidden.id());
        assertNotFound(shared.id());
        assertNotFound(new AnalysisId("r_missing"));
    }

    @Test
    void getDoesNotAnswerAPartialAnalysisThatIsNotCompleted() {
        assertNotFound(store(Visibility.PARTIAL, AnalysisStatus.QUEUED).id());
    }

    @Test
    void listPagesNewestFirstAndTellsWhetherThereIsMore() {
        Analysis oldest = store(Visibility.PARTIAL, AnalysisStatus.COMPLETED);
        Analysis middle = store(Visibility.PARTIAL, AnalysisStatus.COMPLETED);
        Analysis newest = store(Visibility.PARTIAL, AnalysisStatus.COMPLETED);

        PartialAnalysisPage first = service.list(0, 2);
        PartialAnalysisPage second = service.list(1, 2);

        assertThat(first.items()).containsExactly(newest, middle);
        assertThat(first.hasMore()).isTrue();
        assertThat(second.items()).containsExactly(oldest);
        assertThat(second.hasMore()).isFalse();
        assertThat(service.list(0, 3).hasMore()).isFalse();
    }

    @Test
    void listNeverHoldsAPrivateOrPublicAnalysis() {
        Analysis partial = store(Visibility.PARTIAL, AnalysisStatus.COMPLETED);
        store(Visibility.PRIVATE, AnalysisStatus.COMPLETED);
        store(Visibility.PUBLIC, AnalysisStatus.COMPLETED);

        assertThat(service.list(0, 10).items()).containsExactly(partial);
    }

    @Test
    void listDoesNotHoldAPartialAnalysisThatIsNotCompleted() {
        Analysis partial = store(Visibility.PARTIAL, AnalysisStatus.COMPLETED);
        store(Visibility.PARTIAL, AnalysisStatus.QUEUED);

        assertThat(service.list(0, 10).items()).containsExactly(partial);
    }

    private void assertNotFound(AnalysisId id) {
        assertThatThrownBy(() -> service.get(id))
                .isInstanceOfSatisfying(
                        AnalysisException.class, e -> assertThat(e.error()).isEqualTo(AnalysisError.NOT_FOUND));
    }
}
