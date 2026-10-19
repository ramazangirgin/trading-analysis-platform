package tr.girgin.backend.example;

import java.util.Optional;
import org.springframework.http.ResponseEntity;

class Violation {
    ResponseEntity<String> get() {
        return ResponseEntity.ok("body"); // violation
    }

    ResponseEntity<String> find(Optional<String> body) {
        return body.map(ResponseEntity::ok).orElseThrow(); // violation
    }

    ResponseEntity<String> qualified() {
        return org.springframework.http.ResponseEntity.ok().body("body"); // violation
    }
}
