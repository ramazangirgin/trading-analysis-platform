package tr.girgin.backend.trading.analysis.platform.demo;

import org.springframework.stereotype.Service;

@Service
class ViolationService {
    static String format(String value) { // violation
        return value.trim();
    }
}
