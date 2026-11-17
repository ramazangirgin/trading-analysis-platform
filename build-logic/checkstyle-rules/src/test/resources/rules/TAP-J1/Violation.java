package tr.girgin.backend.trading.analysis.platform.demo;

/** Says hello. */ // violation
class Violation {

    /** The greeting. */ // violation
    private final String greeting = "hello";

    String greet() {
        /** Misplaced. */ // violation
        return greeting;
    }
}
