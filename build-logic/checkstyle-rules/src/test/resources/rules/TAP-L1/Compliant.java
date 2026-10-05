package tr.girgin.backend.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Compliant {
    private static final Logger log = LoggerFactory.getLogger(Compliant.class);

    void run(Exception failure) {
        log.info("started");
        log.error("failed", failure);
        failure.printStackTrace(log);
        String text = "System.out.println()";
        // System.err.println("not code");
    }
}
