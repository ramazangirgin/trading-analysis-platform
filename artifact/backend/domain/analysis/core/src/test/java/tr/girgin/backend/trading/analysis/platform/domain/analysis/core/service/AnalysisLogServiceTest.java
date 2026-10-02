package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AnalysisLogServiceTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Incorrect API key provided: sk-proj-abcdEFGH12345678xyz|Incorrect API key provided: sk-proj-abcd****",
                "key=sk-1234567890abcdef|key=sk-1234****",
                "google AIzaSyA1234567890123456|google AIzaSyA1****",
                "nothing secret here, sk- alone|nothing secret here, sk- alone"
            })
    void masksApiKeys(String line, String expected) {
        assertThat(AnalysisLogService.mask(line)).isEqualTo(expected);
    }
}
