package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.AdapterTestSupport;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisFilter;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.PartialAnalysisPage;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Visibility;

class JpaPartialVisibilityTest extends AdapterTestSupport {

    private static final OwnerId OWNER = new OwnerId("zz-owner");
    private static final OwnerId OTHER_OWNER = new OwnerId("zz-other-owner");

    @Autowired
    private JpaAnalysisRepositoryAdapter repository;

    @Autowired
    private DataSource dataSource;

    @Test
    void storesPartialAndFiltersByASetOfVisibilities() {
        Instant base = Instant.parse("2026-09-29T15:00:00Z");
        Analysis partial = shared("ZZPART", OWNER, Visibility.PARTIAL, base);
        Analysis open = shared("ZZPART", OTHER_OWNER, Visibility.PUBLIC, base);
        Analysis closed = inserted("ZZPART", OWNER, base);

        assertThat(repository.findById(partial.id()).orElseThrow().visibility()).isEqualTo(Visibility.PARTIAL);
        assertThat(repository.findAll(new AnalysisFilter(null, "ZZPART", null, Set.of(Visibility.PARTIAL), null)))
                .containsExactly(partial);
        assertThat(repository.findAll(
                        new AnalysisFilter(null, "ZZPART", null, Set.of(Visibility.PUBLIC, Visibility.PARTIAL), null)))
                .containsExactlyInAnyOrder(partial, open)
                .doesNotContain(closed);
        assertThat(repository.findAll(new AnalysisFilter(null, "ZZPART", null, Set.of(), null)))
                .containsExactlyInAnyOrder(partial, open, closed);
    }

    @Test
    void findPageSlicesNewestFirstAndKnowsWhetherThereIsMore() {
        Instant base = Instant.parse("2031-01-01T00:00:00Z");
        Analysis oldest = shared("ZZPAGE", OWNER, Visibility.PARTIAL, base);
        Analysis middle = shared("ZZPAGE", OWNER, Visibility.PARTIAL, base.plusSeconds(1));
        Analysis newest = shared("ZZPAGE", OWNER, Visibility.PARTIAL, base.plusSeconds(2));
        shared("ZZPAGE", OWNER, Visibility.PUBLIC, base.plusSeconds(3));
        AnalysisFilter partial = new AnalysisFilter(null, "ZZPAGE", null, Set.of(Visibility.PARTIAL), null);

        PartialAnalysisPage first = repository.findPage(partial, 0, 2);
        PartialAnalysisPage second = repository.findPage(partial, 1, 2);
        PartialAnalysisPage beyond = repository.findPage(partial, 2, 2);

        assertThat(first.items()).containsExactly(newest, middle);
        assertThat(first.hasMore()).isTrue();
        assertThat(second.items()).containsExactly(oldest);
        assertThat(second.hasMore()).isFalse();
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.hasMore()).isFalse();
        assertThat(repository.findPage(partial, 0, 3).hasMore()).isFalse();
    }

    @Test
    void theVisibilityTypeOfTheDatabaseHasExactlyTheValuesOfTheEnumInOrder() throws SQLException {
        List<String> stored = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet values =
                        statement.executeQuery("SELECT unnest(enum_range(NULL::\"ANALYSIS\".\"VISIBILITY\"))::text")) {
            while (values.next()) {
                stored.add(values.getString(1));
            }
        }

        assertThat(stored)
                .containsExactlyElementsOf(
                        Arrays.stream(Visibility.values()).map(Visibility::name).toList());
    }

    private Analysis inserted(String ticker, OwnerId owner, Instant createdAt) {
        Analysis queued = Analysis.queued(AnalysisId.newId(), spec(ticker), owner, createdAt);
        repository.insert(queued);
        return repository.findById(queued.id()).orElseThrow();
    }

    private Analysis shared(String ticker, OwnerId owner, Visibility visibility, Instant createdAt) {
        return repository.update(inserted(ticker, owner, createdAt).withVisibility(visibility, createdAt));
    }
}
