class SimpleNames {
    void run() {
        Instant.now(); // violation
        java.time.Instant.now(); // violation
        other.Instant.now(); // violation
        Instants.now();
    }
}
