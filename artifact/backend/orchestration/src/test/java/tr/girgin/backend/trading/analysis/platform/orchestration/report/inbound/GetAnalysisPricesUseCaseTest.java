package tr.girgin.backend.trading.analysis.platform.orchestration.report.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisError;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.GetAnalysisUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisAccess;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AssetType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;

class GetAnalysisPricesUseCaseTest {

    private static final OwnerId OWNER = new OwnerId("user-a");
    private static final AnalysisAccess OTHERS = new AnalysisAccess(new OwnerId("user-b"), false);
    private static final AnalysisAccess EVERYONE = new AnalysisAccess(new OwnerId("user-b"), true);

    private final List<ReportKey> priceKeys = new ArrayList<>();
    private final Analysis analysis = Analysis.queued(
            AnalysisId.newId(),
            new AnalysisSpec(
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
                    false),
            OWNER,
            Instant.parse("2026-09-25T10:00:00Z"));
    private final GetAnalysisUseCase getAnalysis = (id, access) -> {
        if (!id.equals(analysis.id()) || !access.canSee(analysis)) {
            throw new AnalysisException(AnalysisError.NOT_FOUND, "Analysis not found: " + id, Map.of());
        }
        return analysis;
    };
    private final GetAnalysisPricesUseCase useCase = new GetAnalysisPricesUseCase(getAnalysis, key -> {
        priceKeys.add(key);
        return Optional.empty();
    });

    @Test
    void anotherUsersPricesAreNotFound() {
        assertThatThrownBy(() -> useCase.getPrices(analysis.id(), OTHERS))
                .isInstanceOfSatisfying(
                        AnalysisException.class, e -> assertThat(e.error()).isEqualTo(AnalysisError.NOT_FOUND));
        assertThat(priceKeys).isEmpty();
    }

    @Test
    void anAllUsersReaderGetsThePricesOfTheAnalysisTickerAndDate() {
        useCase.getPrices(analysis.id(), EVERYONE);

        assertThat(priceKeys).containsExactly(new ReportKey("NVDA", LocalDate.of(2026, 9, 25)));
    }
}
