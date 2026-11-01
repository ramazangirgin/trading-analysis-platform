package tr.girgin.backend.trading.analysis.platform.domain.report.core.service;

import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;

@Service
class ReportKeyService {

    private static final Pattern TICKER = Pattern.compile("\\^?[A-Z0-9][A-Z0-9.\\-=]{0,19}");

    boolean isValidTicker(String ticker) {
        return ticker != null && TICKER.matcher(ticker).matches();
    }

    boolean isValid(ReportKey key) {
        return key != null && isValidTicker(key.ticker());
    }
}
