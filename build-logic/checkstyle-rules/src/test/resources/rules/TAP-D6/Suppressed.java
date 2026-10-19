package tr.girgin.backend.example;

import org.springframework.http.ResponseEntity;

class Suppressed {
    // 200 or 202 depending on the work left: the status varies, so ResponseEntity it is.
    @SuppressWarnings("checkstyle:TAP-D6")
    ResponseEntity<String> poll(boolean done) {
        return done ? ResponseEntity.ok("done") : ResponseEntity.accepted().body("pending");
    }
}
