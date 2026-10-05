import java.time.Instant;

class InstantNow {
    void run(Clock clock) {
        Instant a = Instant.now(); // violation
        Instant b = java.time.Instant.now(); // violation
        Instant c = Instant.now(clock);
        Instant d = Instant.ofEpochSecond(1);
        Instant e = now();
        Instant f = clock.instant();
        Duration g = Duration.now();
    }
}
