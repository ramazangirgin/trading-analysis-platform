package tr.girgin.backend.example;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

class Compliant {
    @GetMapping("/a")
    void get() {}

    @PostMapping
    void post() {}

    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    void events() {}

    // produces = MediaType.APPLICATION_JSON_VALUE is the default.
    void json(Response response) {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    }

    interface Response {
        void setContentType(String type);
    }
}
