package tr.girgin.backend.example;

class Suppressed {
    // A command-line entry point prints to the console by design.
    @SuppressWarnings("checkstyle:TAP-L1")
    void print() {
        System.out.println("usage: ...");
    }
}
