package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisAccessTest {

    private static final OwnerId OWNER = new OwnerId("owner");
    private static final OwnerId OTHER = new OwnerId("other");

    private static final AnalysisAccess OWNER_PLAIN = new AnalysisAccess(OWNER, false, false);
    private static final AnalysisAccess OWNER_SHARER = new AnalysisAccess(OWNER, false, true);
    private static final AnalysisAccess OTHER_PLAIN = new AnalysisAccess(OTHER, false, false);
    private static final AnalysisAccess OTHER_SHARER = new AnalysisAccess(OTHER, false, true);
    private static final AnalysisAccess ADMIN_PLAIN = new AnalysisAccess(OTHER, true, false);
    private static final AnalysisAccess ADMIN_SHARER = new AnalysisAccess(OTHER, true, true);

    private final Analysis privateRun = analysis(Visibility.PRIVATE);
    private final Analysis publicRun = analysis(Visibility.PUBLIC);

    private static Analysis analysis(Visibility visibility) {
        AnalysisSpec spec = new AnalysisSpec(
                "NVDA",
                LocalDate.of(2026, 9, 25),
                AssetType.STOCK,
                List.of(Analyst.MARKET),
                "deepseek",
                "deepseek-v4-flash",
                "deepseek-v4-flash",
                1,
                1,
                "English",
                false);
        Analysis queued = Analysis.queued(new AnalysisId("r_1"), spec, OWNER, Instant.EPOCH);
        return visibility == Visibility.PRIVATE ? queued : queued.withVisibility(visibility, Instant.EPOCH);
    }

    @Test
    void aPrivateAnalysisIsSeenByItsOwnerAndByAllUsersOnly() {
        assertThat(OWNER_PLAIN.canSee(privateRun)).isTrue();
        assertThat(ADMIN_PLAIN.canSee(privateRun)).isTrue();
        assertThat(OTHER_PLAIN.canSee(privateRun)).isFalse();
        assertThat(OTHER_SHARER.canSee(privateRun)).isFalse();
    }

    @Test
    void aPublicAnalysisIsSeenByEveryone() {
        assertThat(OTHER_PLAIN.canSee(publicRun)).isTrue();
        assertThat(OWNER_PLAIN.canSee(publicRun)).isTrue();
        assertThat(ADMIN_PLAIN.canSee(publicRun)).isTrue();
    }

    @Test
    void anAnalysisWithoutAnOwnerIsSeenByAllUsersOnly() {
        Analysis unowned = Analysis.queued(privateRun.id(), privateRun.spec(), null, Instant.EPOCH);

        assertThat(OWNER_PLAIN.canSee(unowned)).isFalse();
        assertThat(ADMIN_PLAIN.canSee(unowned)).isTrue();
    }

    @Test
    void onlyTheOwnerAndAllUsersManageAnAnalysisPublicOrNot() {
        for (Analysis analysis : List.of(privateRun, publicRun)) {
            assertThat(OWNER_PLAIN.canManage(analysis)).isTrue();
            assertThat(ADMIN_PLAIN.canManage(analysis)).isTrue();
            assertThat(OTHER_PLAIN.canManage(analysis)).isFalse();
            assertThat(OTHER_SHARER.canManage(analysis)).isFalse();
        }
    }

    @Test
    void onlyTheOwnerWithShareMakesAnAnalysisPublic() {
        assertThat(OWNER_SHARER.canMakePublic(privateRun)).isTrue();
        assertThat(OWNER_PLAIN.canMakePublic(privateRun)).isFalse();
        assertThat(OTHER_SHARER.canMakePublic(privateRun)).isFalse();
        assertThat(ADMIN_SHARER.canMakePublic(privateRun)).isFalse();
        assertThat(ADMIN_PLAIN.canMakePublic(privateRun)).isFalse();
    }

    @Test
    void theOwnerMakesAPublicAnalysisPrivateWithOrWithoutShare() {
        assertThat(OWNER_PLAIN.canMakePrivate(publicRun)).isTrue();
        assertThat(OWNER_SHARER.canMakePrivate(publicRun)).isTrue();
    }

    @Test
    void allUsersWithShareMakeAnotherUsersPublicAnalysisPrivate() {
        assertThat(ADMIN_SHARER.canMakePrivate(publicRun)).isTrue();
        assertThat(ADMIN_PLAIN.canMakePrivate(publicRun)).isFalse();
        assertThat(OTHER_PLAIN.canMakePrivate(publicRun)).isFalse();
        assertThat(OTHER_SHARER.canMakePrivate(publicRun)).isFalse();
    }

    @Test
    void aPrivateAnalysisCannotBeMadePrivate() {
        assertThat(OWNER_SHARER.canMakePrivate(privateRun)).isFalse();
        assertThat(ADMIN_SHARER.canMakePrivate(privateRun)).isFalse();
    }

    @Test
    void theVisibilityChangeAllowedDependsOnTheCurrentVisibility() {
        assertThat(OWNER_SHARER.canChangeVisibility(privateRun)).isTrue();
        assertThat(OWNER_PLAIN.canChangeVisibility(privateRun)).isFalse();
        assertThat(ADMIN_SHARER.canChangeVisibility(privateRun)).isFalse();

        assertThat(OWNER_PLAIN.canChangeVisibility(publicRun)).isTrue();
        assertThat(ADMIN_SHARER.canChangeVisibility(publicRun)).isTrue();
        assertThat(ADMIN_PLAIN.canChangeVisibility(publicRun)).isFalse();
        assertThat(OTHER_SHARER.canChangeVisibility(publicRun)).isFalse();
    }
}
