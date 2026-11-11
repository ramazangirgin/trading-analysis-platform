package tr.girgin.backend.example;

class Compliant {

    private final UserRepositoryPort userRepositoryPort;

    Compliant(UserRepositoryPort userRepositoryPort, ReportService reportService) {
        this.userRepositoryPort = userRepositoryPort;
    }

    void run(UserManagementService strictUserManagementService) {
        UserMapper userMapper = null;
        PaymentGateway gateway = null;
    }
}
