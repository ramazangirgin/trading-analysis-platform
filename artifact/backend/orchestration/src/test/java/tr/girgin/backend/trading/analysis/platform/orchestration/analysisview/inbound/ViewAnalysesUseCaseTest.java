package tr.girgin.backend.trading.analysis.platform.orchestration.analysisview.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.AnalysisRights;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisAccess;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AssetType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.TestAnalyses;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Visibility;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.UserDirectoryUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.orchestration.analysisview.model.AnalysisView;

class ViewAnalysesUseCaseTest {

    private static final AnalysisAccess ACCESS = new AnalysisAccess(new OwnerId("u-1"), false, true);
    private static final AnalysisRights MANAGER =
            new AnalysisRights(true, true, List.of(Visibility.PRIVATE, Visibility.PUBLIC));
    private static final AnalysisRights READER = new AnalysisRights(false, false, List.of());

    private final List<Collection<UserId>> lookups = new ArrayList<>();
    private final Map<UserId, Username> directory = Map.of(new UserId("u-1"), new Username("alice"));

    private final ViewAnalysesUseCase useCase = new ViewAnalysesUseCase(
            (analysis, _) ->
                    analysis.owner() != null && analysis.owner().value().equals("u-1") ? MANAGER : READER,
            new UserDirectoryUseCase() {
                @Override
                public Optional<UserId> firstAdmin() {
                    return Optional.empty();
                }

                @Override
                public Map<UserId, Username> usernames(Collection<UserId> ids) {
                    lookups.add(ids);
                    return directory;
                }
            });

    @Test
    void givesEachAnalysisOfAListItsOwnRightsInOrder() {
        Analysis mine = analysis(new OwnerId("u-1"));
        Analysis theirs = analysis(new OwnerId("u-2"));

        List<AnalysisView> views = useCase.view(List.of(mine, theirs), ACCESS);

        assertThat(views).extracting(AnalysisView::analysis).containsExactly(mine, theirs);
        assertThat(views).extracting(AnalysisView::rights).containsExactly(MANAGER, READER);
    }

    @Test
    void setsTheUsernameOfTheOwner() {
        AnalysisView view = useCase.view(analysis(new OwnerId("u-1")), ACCESS);

        assertThat(view.owner().id()).isEqualTo(new OwnerId("u-1"));
        assertThat(view.owner().username()).isEqualTo(new Username("alice"));
    }

    @Test
    void leavesTheUsernameNullWhenTheOwnerIsNoLongerAUser() {
        AnalysisView view = useCase.view(analysis(new OwnerId("u-2")), ACCESS);

        assertThat(view.owner().id()).isEqualTo(new OwnerId("u-2"));
        assertThat(view.owner().username()).isNull();
    }

    @Test
    void leavesTheOwnerNullWhenTheAnalysisHasNone() {
        AnalysisView view = useCase.view(analysis(null), ACCESS);

        assertThat(view.owner()).isNull();
    }

    @Test
    void looksTheUsernamesOfAWholeListUpOnce() {
        useCase.view(List.of(analysis(new OwnerId("u-1")), analysis(new OwnerId("u-2")), analysis(null)), ACCESS);

        assertThat(lookups).hasSize(1);
        assertThat(lookups.getFirst()).containsExactlyInAnyOrder(new UserId("u-1"), new UserId("u-2"));
    }

    private Analysis analysis(OwnerId owner) {
        AnalysisSpec spec = new AnalysisSpec(
                "AAPL",
                LocalDate.of(2026, 1, 2),
                AssetType.STOCK,
                List.of(Analyst.MARKET),
                "openai",
                "deep",
                "quick",
                1,
                1,
                "English",
                false);
        return TestAnalyses.queued(TestAnalyses.newId(), spec, owner, Instant.parse("2026-01-02T10:00:00Z"));
    }
}
