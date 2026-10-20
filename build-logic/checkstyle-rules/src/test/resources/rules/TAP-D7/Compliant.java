package tr.girgin.backend.trading.analysis.platform.demo;

import org.springframework.stereotype.Service;

@Service
class CompliantService {
    static final int LIMIT = 3;

    String format(String value) {
        return value.trim();
    }

    static class Parser {
        static int parse(String value) {
            return Integer.parseInt(value);
        }
    }
}

class PlainHelper {
    static String format(String value) {
        return value.trim();
    }
}
