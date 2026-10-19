package tr.girgin.backend.example;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

class Violation {
    @GetMapping(path = "/a", produces = MediaType.APPLICATION_JSON_VALUE) // violation
    void get() {}

    @PostMapping(
            path = "/b",
            consumes = MediaType.APPLICATION_JSON_VALUE, // violation
            produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE) // violation
    void post() {}

    @PostMapping(path = "/c", consumes = {"application/json"}) // violation
    void literal() {}
}
