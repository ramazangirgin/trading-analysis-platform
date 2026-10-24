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
    private final Analysis partialRun = analysis(Visibility.PARTIAL);

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
        for (Analysis analysis : List.of(privateRun, publicRun, partialRun)) {
            assertThat(OWNER_PLAIN.canManage(analysis)).isTrue();
            assertThat(ADMIN_PLAIN.canManage(analysis)).isTrue();
            assertThat(OTHER_PLAIN.canManage(analysis)).isFalse();
            assertThat(OTHER_SHARER.canManage(analysis)).isFalse();
        }
    }

    @Test
    void aPartialAnalysisIsSeenByEveryLoggedInUser() {
        assertThat(OTHER_PLAIN.canSee(partialRun)).isTrue();
        assertThat(OWNER_PLAIN.canSee(partialRun)).isTrue();
        assertThat(ADMIN_PLAIN.canSee(partialRun)).isTrue();
    }

    @Test
    void onlyTheSharedVisibilitiesAreSharedWithUsers() {
        assertThat(Visibility.PRIVATE.sharedWithUsers()).isFalse();
        assertThat(Visibility.PUBLIC.sharedWithUsers()).isTrue();
        assertThat(Visibility.PARTIAL.sharedWithUsers()).isTrue();
    }

    @Test
    void canChangeToCoversEveryCaller() {
        assertAllowed(privateRun, Visibility.PUBLIC, OWNER_SHARER);
        assertAllowed(privateRun, Visibility.PARTIAL, OWNER_SHARER);
        assertAllowed(privateRun, Visibility.PRIVATE);

        assertAllowed(publicRun, Visibility.PRIVATE, OWNER_PLAIN, OWNER_SHARER, ADMIN_SHARER);
        assertAllowed(publicRun, Visibility.PARTIAL, OWNER_SHARER);
        assertAllowed(publicRun, Visibility.PUBLIC);

        assertAllowed(partialRun, Visibility.PRIVATE, OWNER_PLAIN, OWNER_SHARER, ADMIN_SHARER);
        assertAllowed(partialRun, Visibility.PUBLIC, OWNER_SHARER, ADMIN_SHARER);
        assertAllowed(partialRun, Visibility.PARTIAL);
    }

    @Test
    void allowedVisibilitiesListsTheTargetsInEnumOrder() {
        assertThat(OWNER_SHARER.allowedVisibilities(privateRun)).containsExactly(Visibility.PUBLIC, Visibility.PARTIAL);
        assertThat(OWNER_PLAIN.allowedVisibilities(privateRun)).isEmpty();
        assertThat(ADMIN_SHARER.allowedVisibilities(privateRun)).isEmpty();

        assertThat(OWNER_SHARER.allowedVisibilities(publicRun)).containsExactly(Visibility.PRIVATE, Visibility.PARTIAL);
        assertThat(OWNER_PLAIN.allowedVisibilities(publicRun)).containsExactly(Visibility.PRIVATE);
        assertThat(ADMIN_SHARER.allowedVisibilities(publicRun)).containsExactly(Visibility.PRIVATE);
        assertThat(ADMIN_PLAIN.allowedVisibilities(publicRun)).isEmpty();
        assertThat(OTHER_SHARER.allowedVisibilities(publicRun)).isEmpty();

        assertThat(OWNER_SHARER.allowedVisibilities(partialRun)).containsExactly(Visibility.PRIVATE, Visibility.PUBLIC);
        assertThat(ADMIN_SHARER.allowedVisibilities(partialRun)).containsExactly(Visibility.PRIVATE, Visibility.PUBLIC);
        assertThat(ADMIN_PLAIN.allowedVisibilities(partialRun)).isEmpty();
        assertThat(OTHER_SHARER.allowedVisibilities(partialRun)).isEmpty();
    }

    // Asserts that exactly the given callers, out of every kind of caller, may change the analysis to target.
    private static void assertAllowed(Analysis analysis, Visibility target, AnalysisAccess... allowed) {
        List<AnalysisAccess> everyone =
                List.of(OWNER_PLAIN, OWNER_SHARER, OTHER_PLAIN, OTHER_SHARER, ADMIN_PLAIN, ADMIN_SHARER);
        for (AnalysisAccess caller : everyone) {
            assertThat(caller.canChangeTo(analysis, target))
                    .as("%s -> %s by %s", analysis.visibility(), target, caller)
                    .isEqualTo(List.of(allowed).contains(caller));
        }
    }
}
