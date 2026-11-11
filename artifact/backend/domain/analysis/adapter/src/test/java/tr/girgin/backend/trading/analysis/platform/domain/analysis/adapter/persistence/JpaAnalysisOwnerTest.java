package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.AdapterTestSupport;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisFilter;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.TestAnalyses;

class JpaAnalysisOwnerTest extends AdapterTestSupport {

    private static final OwnerId OWNER = new OwnerId("zz-owner");
    private static final OwnerId OTHER_OWNER = new OwnerId("zz-other-owner");

    @Autowired
    private JpaAnalysisRepositoryAdapter jpaAnalysisRepositoryAdapter;

    @Test
    void storesTheOwnerAndFiltersByIt() {
        Instant base = Instant.parse("2026-09-29T12:00:00Z");
        Analysis mine = inserted(TestAnalyses.queued(TestAnalyses.newId(), spec("ZZOWN"), OWNER, base));
        Analysis theirs = inserted(TestAnalyses.queued(TestAnalyses.newId(), spec("ZZOWN"), OTHER_OWNER, base));
        Analysis nobodys = inserted(TestAnalyses.queued(TestAnalyses.newId(), spec("ZZOWN"), null, base));

        assertThat(mine.owner()).isEqualTo(OWNER);
        assertThat(nobodys.owner()).isNull();
        assertThat(jpaAnalysisRepositoryAdapter.findAll(new AnalysisFilter(null, "ZZOWN", OWNER, Set.of(), null)))
                .containsExactly(mine);
        assertThat(jpaAnalysisRepositoryAdapter.findAll(new AnalysisFilter(null, "ZZOWN", OTHER_OWNER, Set.of(), null)))
                .containsExactly(theirs);
        assertThat(jpaAnalysisRepositoryAdapter.findAll(new AnalysisFilter(null, "ZZOWN", null, Set.of(), null)))
                .containsExactlyInAnyOrder(mine, theirs, nobodys);
    }

    @Test
    void assignOwnerWhereMissingTouchesOnlyRowsWithoutOneAndRaisesTheirVersion() {
        Instant base = Instant.parse("2026-09-29T13:00:00Z");
        Analysis owned = inserted(TestAnalyses.queued(TestAnalyses.newId(), spec("ZZASSIGN"), OWNER, base));
        Analysis unowned = inserted(TestAnalyses.queued(TestAnalyses.newId(), spec("ZZASSIGN"), null, base));

        int assigned = jpaAnalysisRepositoryAdapter.assignOwnerWhereMissing(OTHER_OWNER);

        assertThat(assigned).isGreaterThanOrEqualTo(1);
        Analysis nowOwned = jpaAnalysisRepositoryAdapter.findById(unowned.id()).orElseThrow();
        assertThat(nowOwned.owner()).isEqualTo(OTHER_OWNER);
        assertThat(nowOwned.version()).isEqualTo(unowned.version() + 1);
        assertThat(jpaAnalysisRepositoryAdapter.findById(owned.id())).contains(owned);
        assertThat(jpaAnalysisRepositoryAdapter.assignOwnerWhereMissing(OTHER_OWNER))
                .isZero();
    }

    private Analysis inserted(Analysis analysis) {
        jpaAnalysisRepositoryAdapter.insert(analysis);
        return jpaAnalysisRepositoryAdapter.findById(analysis.id()).orElseThrow();
    }
}
