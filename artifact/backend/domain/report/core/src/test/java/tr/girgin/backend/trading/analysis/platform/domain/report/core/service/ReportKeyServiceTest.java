package tr.girgin.backend.trading.analysis.platform.domain.report.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;

class ReportKeyServiceTest {

    private final ReportKeyService reportKeyService = new ReportKeyService();

    @ParameterizedTest
    @ValueSource(strings = {"MU", "GOOG", "BRK.B", "BTC-USD", "^GSPC", "EURUSD=X", "A", "12345678901234567890"})
    void acceptsTickers(String ticker) {
        assertThat(reportKeyService.isValidTicker(ticker)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"", " ", "mu", "../etc", "a/b", "MU/..", ".MU", "-MU", "^", "MU MU", "123456789012345678901"})
    void rejectsTickers(String ticker) {
        assertThat(reportKeyService.isValidTicker(ticker)).isFalse();
    }

    @Test
    void rejectsNullTicker() {
        assertThat(reportKeyService.isValidTicker(null)).isFalse();
    }

    @Test
    void checksTheTickerOfAKey() {
        assertThat(reportKeyService.isValid(new ReportKey("MU", LocalDate.of(2026, 1, 2))))
                .isTrue();
        assertThat(reportKeyService.isValid(null)).isFalse();
    }
}
