class MultiplePatterns {
    void run(Other other) {
        System.out.println(); // violation
        other.out.println(); // violation
        other.err.println();
    }
}
