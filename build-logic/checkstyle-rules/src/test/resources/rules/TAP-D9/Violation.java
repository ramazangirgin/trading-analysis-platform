package tr.girgin.backend.example;

class Violation {

    private final UserRepositoryPort users; // violation

    Violation(UserRepositoryPort userRepositoryPort, ReportService reports) { // violation
        this.users = userRepositoryPort;
    }

    void run(ReportService reportService) {
        UserMapper mapper = null; // violation
    }
}
