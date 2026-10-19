package tr.girgin.backend.example;

import java.net.URI;
import org.springframework.http.ResponseEntity;

class Compliant {
    String get() {
        return "body";
    }

    ResponseEntity<String> create() {
        return ResponseEntity.created(URI.create("/api/things/1")).body("body");
    }

    ResponseEntity<Void> delete() {
        // Not ResponseEntity.ok(...): 204 has no body.
        return ResponseEntity.noContent().build();
    }
}
