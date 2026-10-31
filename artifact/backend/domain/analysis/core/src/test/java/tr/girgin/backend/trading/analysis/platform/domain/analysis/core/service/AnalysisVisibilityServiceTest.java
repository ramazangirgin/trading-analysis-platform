package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.OptimisticLockingFailureException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisError;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisAccess;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Visibility;

class AnalysisVisibilityServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T05:06:07Z");

    private Fakes.Repository repository;
    private AnalysisVisibilityService service;
    private AnalysisId id;

    @BeforeEach
    void setUp() {
        repository = new Fakes.Repository();
        service = new AnalysisVisibilityService(
                repository,
                new AnalysisAccessService(),
                new AnalysisTransitionService(new AnalysisSpecService()),
                new AnalysisIdService(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        id = store(AnalysisStatus.COMPLETED, Visibility.PRIVATE);
    }

    private AnalysisId store(AnalysisStatus status, Visibility visibility) {
        Analysis queued = Analysis.queued(AnalysisId.newId(), Fakes.spec("NVDA"), Fakes.OWNER, Instant.EPOCH);
        Analysis stored = switch (status) {
            case QUEUED -> queued;
            case RUNNING -> queued.running(Instant.EPOCH, "4242");
            default -> queued.finished(status, Instant.EPOCH, null, null);
        };
        repository.insert(stored.withVisibility(visibility, Instant.EPOCH));
        return stored.id();
    }

    private Analysis change(AnalysisId target, Visibility visibility, AnalysisAccess access) {
        return service.change(target, visibility, access, null);
    }

    @Test
    void theOwnerWithShareMakesACompletedAnalysisPublicAndPrivateAgain() {
        Analysis shared = change(id, Visibility.PUBLIC, Fakes.OWN_SHARER);

        assertThat(shared.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(shared.visibilityChangedAt()).isEqualTo(NOW);
        assertThat(repository.rows.get(id)).isEqualTo(shared);

        Analysis withdrawn = change(id, Visibility.PRIVATE, Fakes.OWN_SHARER);

        assertThat(withdrawn.visibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(withdrawn.visibilityChangedAt()).isEqualTo(NOW);
    }

    @Test
    void theOwnerWithShareMakesACompletedAnalysisPartialThenPublicThenPrivate() {
        Analysis partial = change(id, Visibility.PARTIAL, Fakes.OWN_SHARER);

        assertThat(partial.visibility()).isEqualTo(Visibility.PARTIAL);
        assertThat(partial.visibilityChangedAt()).isEqualTo(NOW);
        assertThat(repository.rows.get(id)).isEqualTo(partial);
        assertThat(change(id, Visibility.PUBLIC, Fakes.OWN_SHARER).visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(change(id, Visibility.PRIVATE, Fakes.OWN_SHARER).visibility())
                .isEqualTo(Visibility.PRIVATE);
    }

    @ParameterizedTest
    @EnumSource(
            value = AnalysisStatus.class,
            names = {"QUEUED", "RUNNING", "FAILED", "STOPPED"})
    void onlyACompletedAnalysisCanBeMadePublicOrPartial(AnalysisStatus status) {
        AnalysisId notDone = store(status, Visibility.PRIVATE);

        for (Visibility target : List.of(Visibility.PUBLIC, Visibility.PARTIAL)) {
            assertThatThrownBy(() -> change(notDone, target, Fakes.OWN_SHARER))
                    .isInstanceOfSatisfying(AnalysisException.class, e -> {
                        assertThat(e.error()).isEqualTo(AnalysisError.NOT_SHAREABLE);
                        assertThat(e.params()).containsEntry("status", status.name());
                    });
        }
        assertThat(repository.updates).isZero();
    }

    @ParameterizedTest
    @EnumSource(
            value = Visibility.class,
            names = {"PUBLIC", "PARTIAL"})
    void theOwnerWithoutShareCannotShareButCanMakeItPrivate(Visibility target) {
        assertThatThrownBy(() -> change(id, target, Fakes.OWN))
                .isInstanceOfSatisfying(
                        AnalysisException.class, e -> assertThat(e.error()).isEqualTo(AnalysisError.SHARE_FORBIDDEN));
        AnalysisId partial = store(AnalysisStatus.COMPLETED, Visibility.PARTIAL);
        AnalysisId shared = store(AnalysisStatus.COMPLETED, Visibility.PUBLIC);

        assertThat(change(partial, Visibility.PRIVATE, Fakes.OWN).visibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(change(shared, Visibility.PRIVATE, Fakes.OWN).visibility()).isEqualTo(Visibility.PRIVATE);
    }

    @Test
    void anAdminWithShareMovesAnotherUsersPartialAnalysisToPublicAndToPrivate() {
        AnalysisId partial = store(AnalysisStatus.COMPLETED, Visibility.PARTIAL);

        assertThat(change(partial, Visibility.PUBLIC, Fakes.EVERYONE_SHARER).visibility())
                .isEqualTo(Visibility.PUBLIC);
        assertThat(change(partial, Visibility.PRIVATE, Fakes.EVERYONE_SHARER).visibility())
                .isEqualTo(Visibility.PRIVATE);
    }

    @Test
    void anAdminNeverMakesAnyonesAnalysisPartial() {
        AnalysisId shared = store(AnalysisStatus.COMPLETED, Visibility.PUBLIC);

        assertNotFound(id, Visibility.PARTIAL, Fakes.EVERYONE_SHARER);
        assertNotFound(shared, Visibility.PARTIAL, Fakes.EVERYONE_SHARER);
        assertThat(repository.updates).isZero();
    }

    @Test
    void anAdminWithoutShareDoesNotFindAPartialAnalysisToMakePublic() {
        AnalysisId partial = store(AnalysisStatus.COMPLETED, Visibility.PARTIAL);

        assertNotFound(partial, Visibility.PUBLIC, Fakes.EVERYONE);
        assertThat(repository.updates).isZero();
    }

    @Test
    void anotherUsersPartialAnalysisCannotBeChangedByAUserWithoutAllUsers() {
        AnalysisId partial = store(AnalysisStatus.COMPLETED, Visibility.PARTIAL);

        assertNotFound(partial, Visibility.PUBLIC, Fakes.OTHERS);
        assertNotFound(partial, Visibility.PRIVATE, Fakes.OTHERS);
        assertThat(repository.updates).isZero();
    }

    @Test
    void anotherUserDoesNotFindAPrivateOrAPublicAnalysisToChange() {
        AnalysisId shared = store(AnalysisStatus.COMPLETED, Visibility.PUBLIC);

        assertNotFound(id, Visibility.PUBLIC, Fakes.OTHERS);
        assertNotFound(id, Visibility.PRIVATE, Fakes.OTHERS);
        assertNotFound(shared, Visibility.PRIVATE, Fakes.OTHERS);
        assertThat(repository.updates).isZero();
    }

    @Test
    void anAdminWithoutShareDoesNotFindAPublicAnalysisToMakePrivate() {
        AnalysisId shared = store(AnalysisStatus.COMPLETED, Visibility.PUBLIC);

        assertNotFound(shared, Visibility.PRIVATE, Fakes.EVERYONE);
        assertThat(repository.rows.get(shared).visibility()).isEqualTo(Visibility.PUBLIC);
    }

    @Test
    void anAdminWithShareMakesAnotherUsersPublicAnalysisPrivateButNeverPublic() {
        AnalysisId shared = store(AnalysisStatus.COMPLETED, Visibility.PUBLIC);

        assertThat(change(shared, Visibility.PRIVATE, Fakes.EVERYONE_SHARER).visibility())
                .isEqualTo(Visibility.PRIVATE);
        assertNotFound(shared, Visibility.PUBLIC, Fakes.EVERYONE_SHARER);
        assertNotFound(id, Visibility.PUBLIC, Fakes.EVERYONE_SHARER);
    }

    @Test
    void aStaleVersionFailsAndWritesNothing() {
        Analysis stored = repository.rows.get(id);
        repository.rows.put(
                id,
                new Analysis(
                        stored.id(),
                        stored.spec(),
                        stored.status(),
                        stored.source(),
                        stored.rating(),
                        stored.decision(),
                        stored.stats(),
                        stored.createdAt(),
                        stored.startedAt(),
                        stored.endedAt(),
                        stored.errorCode(),
                        stored.errorMessage(),
                        stored.externalRef(),
                        stored.runnerRef(),
                        stored.owner(),
                        stored.visibility(),
                        stored.visibilityChangedAt(),
                        3L));

        assertThatThrownBy(() -> service.change(id, Visibility.PUBLIC, Fakes.OWN_SHARER, 2L))
                .isInstanceOfSatisfying(
                        AnalysisException.class, e -> assertThat(e.error()).isEqualTo(AnalysisError.CONCURRENT_UPDATE));
        assertThat(repository.updates).isZero();
        assertThat(service.change(id, Visibility.PUBLIC, Fakes.OWN_SHARER, 3L).visibility())
                .isEqualTo(Visibility.PUBLIC);
    }

    @Test
    void theVisibilityItAlreadyHasIsReturnedWithoutAWrite() {
        Analysis unchanged = change(id, Visibility.PRIVATE, Fakes.OWN_SHARER);

        assertThat(unchanged).isEqualTo(repository.rows.get(id));
        assertThat(unchanged.visibilityChangedAt()).isEqualTo(Instant.EPOCH);
        assertThat(repository.updates).isZero();
    }

    @Test
    void anOptimisticLockFailureFromTheStoreIsAConcurrentUpdate() {
        repository.failOnWrite = new OptimisticLockingFailureException("stale");

        assertThatThrownBy(() -> change(id, Visibility.PUBLIC, Fakes.OWN_SHARER))
                .isInstanceOfSatisfying(
                        AnalysisException.class, e -> assertThat(e.error()).isEqualTo(AnalysisError.CONCURRENT_UPDATE));
    }

    @Test
    void anUnknownAnalysisIsNotFound() {
        assertNotFound(new AnalysisId("r_missing"), Visibility.PUBLIC, Fakes.OWN_SHARER);
    }

    private void assertNotFound(AnalysisId target, Visibility visibility, AnalysisAccess access) {
        assertThatThrownBy(() -> change(target, visibility, access))
                .isInstanceOfSatisfying(
                        AnalysisException.class, e -> assertThat(e.error()).isEqualTo(AnalysisError.NOT_FOUND));
    }
}
