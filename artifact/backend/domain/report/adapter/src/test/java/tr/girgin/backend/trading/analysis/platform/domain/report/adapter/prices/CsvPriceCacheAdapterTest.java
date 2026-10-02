package tr.girgin.backend.trading.analysis.platform.domain.report.adapter.prices;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.PriceBar;

class CsvPriceCacheAdapterTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 29);
    private static final String HEADER = "Date,Close,High,Low,Open,Volume\n";

    @TempDir
    private Path cache;

    private void write(String name, String body) throws IOException {
        Files.writeString(cache.resolve(name), HEADER + body);
    }

    @Test
    void readsTheFileReachingFurthest() throws IOException {
        write("MU-YFin-data-2021-09-27-2026-09-27.csv", "2026-09-25,1.0,1,1,1,10\n");
        write(
                "MU-YFin-data-2021-09-28-2026-09-29.csv",
                "2026-09-25,1082.28,1108.72,1073.0,1095.83,20870400\n"
                        + "2026-09-28,1053.98,1084.81,1032.0,1075.98,2.21376E7\n");
        write("MUX-YFin-data-2021-09-28-2026-09-30.csv", "2026-09-29,5.0,5,5,5,5\n");

        assertThat(new CsvPriceCacheAdapter(cache).read("MU", DAY))
                .containsExactly(
                        new PriceBar(LocalDate.of(2026, 9, 25), 1095.83, 1108.72, 1073.0, 1082.28, 20870400),
                        new PriceBar(LocalDate.of(2026, 9, 28), 1075.98, 1084.81, 1032.0, 1053.98, 22137600));
    }

    @Test
    void fallsBackToTheUnrangedFileAndSkipsRowsThatDoNotParse() throws IOException {
        write("BTC-USD-YFin-data.csv", "2026-09-28,100.5,101,99,100,7\nnot,a,row\n2026-09-29,,1,1,1,1\n");

        assertThat(new CsvPriceCacheAdapter(cache).read("BTC-USD", DAY))
                .extracting(PriceBar::close)
                .containsExactly(100.5);
    }

    @Test
    void isEmptyForAnUnknownTickerOrAMissingDirectory() {
        assertThat(new CsvPriceCacheAdapter(cache).read("NVDA", DAY)).isEmpty();
        assertThat(new CsvPriceCacheAdapter(cache.resolve("missing")).read("NVDA", DAY))
                .isEmpty();
    }
}
