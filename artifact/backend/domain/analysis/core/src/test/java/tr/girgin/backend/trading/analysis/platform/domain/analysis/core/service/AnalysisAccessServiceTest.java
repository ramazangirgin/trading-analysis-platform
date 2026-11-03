package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.AnalysisRights;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisAccess;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AssetType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.TestAnalyses;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Visibility;

class AnalysisAccessServiceTest {

    private static final OwnerId OWNER = new OwnerId("owner");
    private static final OwnerId OTHER = new OwnerId("other");

    private static final AnalysisAccess OWNER_PLAIN = new AnalysisAccess(OWNER, false, false);
    private static final AnalysisAccess OWNER_SHARER = new AnalysisAccess(OWNER, false, true);
    private static final AnalysisAccess OTHER_PLAIN = new AnalysisAccess(OTHER, false, false);
    private static final AnalysisAccess OTHER_SHARER = new AnalysisAccess(OTHER, false, true);
    private static final AnalysisAccess ADMIN_PLAIN = new AnalysisAccess(OTHER, true, false);
    private static final AnalysisAccess ADMIN_SHARER = new AnalysisAccess(OTHER, true, true);

    private final AnalysisAccessService analysisAccessService = new AnalysisAccessService();

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
        Analysis queued = Fakes.TRANSITIONS.queued(new AnalysisId("r_1"), spec, OWNER, Instant.EPOCH);
        return visibility == Visibility.PRIVATE
                ? queued
                : Fakes.TRANSITIONS.withVisibility(queued, visibility, Instant.EPOCH);
    }

    @Test
    void aPrivateAnalysisIsSeenByItsOwnerAndByAllUsersOnly() {
        assertThat(analysisAccessService.canSee(privateRun, OWNER_PLAIN)).isTrue();
        assertThat(analysisAccessService.canSee(privateRun, ADMIN_PLAIN)).isTrue();
        assertThat(analysisAccessService.canSee(privateRun, OTHER_PLAIN)).isFalse();
        assertThat(analysisAccessService.canSee(privateRun, OTHER_SHARER)).isFalse();
    }

    @Test
    void aPublicAnalysisIsSeenByEveryone() {
        assertThat(analysisAccessService.canSee(publicRun, OTHER_PLAIN)).isTrue();
        assertThat(analysisAccessService.canSee(publicRun, OWNER_PLAIN)).isTrue();
        assertThat(analysisAccessService.canSee(publicRun, ADMIN_PLAIN)).isTrue();
    }

    @Test
    void anAnalysisWithoutAnOwnerIsSeenByAllUsersOnly() {
        Analysis unowned = TestAnalyses.queued(privateRun.id(), privateRun.spec(), null, Instant.EPOCH);

        assertThat(analysisAccessService.canSee(unowned, OWNER_PLAIN)).isFalse();
        assertThat(analysisAccessService.canSee(unowned, ADMIN_PLAIN)).isTrue();
    }

    @Test
    void onlyTheOwnerAndAllUsersManageAnAnalysisPublicOrNot() {
        for (Analysis analysis : List.of(privateRun, publicRun, partialRun)) {
            assertThat(analysisAccessService.canManage(analysis, OWNER_PLAIN)).isTrue();
            assertThat(analysisAccessService.canManage(analysis, ADMIN_PLAIN)).isTrue();
            assertThat(analysisAccessService.canManage(analysis, OTHER_PLAIN)).isFalse();
            assertThat(analysisAccessService.canManage(analysis, OTHER_SHARER)).isFalse();
        }
    }

    @Test
    void aPartialAnalysisIsSeenByEveryLoggedInUser() {
        assertThat(analysisAccessService.canSee(partialRun, OTHER_PLAIN)).isTrue();
        assertThat(analysisAccessService.canSee(partialRun, OWNER_PLAIN)).isTrue();
        assertThat(analysisAccessService.canSee(partialRun, ADMIN_PLAIN)).isTrue();
    }

    @Test
    void onlyTheSharedVisibilitiesAreSharedWithUsers() {
        assertThat(analysisAccessService.sharedWithUsers(Visibility.PRIVATE)).isFalse();
        assertThat(analysisAccessService.sharedWithUsers(Visibility.PUBLIC)).isTrue();
        assertThat(analysisAccessService.sharedWithUsers(Visibility.PARTIAL)).isTrue();
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
        assertThat(analysisAccessService.allowedVisibilities(privateRun, OWNER_SHARER))
                .containsExactly(Visibility.PUBLIC, Visibility.PARTIAL);
        assertThat(analysisAccessService.allowedVisibilities(privateRun, OWNER_PLAIN))
                .isEmpty();
        assertThat(analysisAccessService.allowedVisibilities(privateRun, ADMIN_SHARER))
                .isEmpty();

        assertThat(analysisAccessService.allowedVisibilities(publicRun, OWNER_SHARER))
                .containsExactly(Visibility.PRIVATE, Visibility.PARTIAL);
        assertThat(analysisAccessService.allowedVisibilities(publicRun, OWNER_PLAIN))
                .containsExactly(Visibility.PRIVATE);
        assertThat(analysisAccessService.allowedVisibilities(publicRun, ADMIN_SHARER))
                .containsExactly(Visibility.PRIVATE);
        assertThat(analysisAccessService.allowedVisibilities(publicRun, ADMIN_PLAIN))
                .isEmpty();
        assertThat(analysisAccessService.allowedVisibilities(publicRun, OTHER_SHARER))
                .isEmpty();

        assertThat(analysisAccessService.allowedVisibilities(partialRun, OWNER_SHARER))
                .containsExactly(Visibility.PRIVATE, Visibility.PUBLIC);
        assertThat(analysisAccessService.allowedVisibilities(partialRun, ADMIN_SHARER))
                .containsExactly(Visibility.PRIVATE, Visibility.PUBLIC);
        assertThat(analysisAccessService.allowedVisibilities(partialRun, ADMIN_PLAIN))
                .isEmpty();
        assertThat(analysisAccessService.allowedVisibilities(partialRun, OTHER_SHARER))
                .isEmpty();
    }

    @Test
    void rightsOfAnOwnerWithShareManageAndChangeToTheOtherVisibilities() {
        assertThat(analysisAccessService.rights(privateRun, OWNER_SHARER))
                .isEqualTo(new AnalysisRights(true, true, List.of(Visibility.PUBLIC, Visibility.PARTIAL)));
    }

    @Test
    void rightsOfAnOwnerWithoutShareMayOnlyGoBackToPrivate() {
        assertThat(analysisAccessService.rights(publicRun, OWNER_PLAIN))
                .isEqualTo(new AnalysisRights(true, true, List.of(Visibility.PRIVATE)));
        assertThat(analysisAccessService.rights(privateRun, OWNER_PLAIN))
                .isEqualTo(new AnalysisRights(true, false, List.of()));
    }

    @Test
    void rightsOfAnotherUserAreEmpty() {
        assertThat(analysisAccessService.rights(publicRun, OTHER_SHARER))
                .isEqualTo(new AnalysisRights(false, false, List.of()));
    }

    @Test
    void rightsOfAnAdminOnSomeoneElsesPartialAnalysisNarrowOrPublish() {
        assertThat(analysisAccessService.rights(partialRun, ADMIN_SHARER))
                .isEqualTo(new AnalysisRights(true, true, List.of(Visibility.PRIVATE, Visibility.PUBLIC)));
        assertThat(analysisAccessService.rights(partialRun, ADMIN_PLAIN))
                .isEqualTo(new AnalysisRights(true, false, List.of()));
    }

    private void assertAllowed(Analysis analysis, Visibility target, AnalysisAccess... allowed) {
        List<AnalysisAccess> everyone =
                List.of(OWNER_PLAIN, OWNER_SHARER, OTHER_PLAIN, OTHER_SHARER, ADMIN_PLAIN, ADMIN_SHARER);
        for (AnalysisAccess caller : everyone) {
            assertThat(analysisAccessService.canChangeTo(analysis, caller, target))
                    .as("%s -> %s by %s", analysis.visibility(), target, caller)
                    .isEqualTo(List.of(allowed).contains(caller));
        }
    }
}
