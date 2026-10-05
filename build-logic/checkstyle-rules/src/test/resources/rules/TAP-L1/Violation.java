package tr.girgin.backend.example;

class Violation {
    void run(Exception failure) {
        System.out.println("started"); // violation
        System.err.println("failed"); // violation
        failure.printStackTrace(); // violation
        java.lang.System.out.printf("%d", 1); // violation
    }
}
