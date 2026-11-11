package demo;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class Declarations {

    private final UserRepositoryPort users; // violation

    private final UserRepositoryPort userRepositoryPort;

    private final List<OrderService> orderServices;

    private final OrderService<String> orders; // violation

    private final com.example.OrderService qualified; // violation

    private final com.example.OrderService<String> qualifiedGeneric; // violation

    private final com.example.OrderService qualifiedExact; // violation

    private final com.example.OrderService orderService;

    private final Outer.InnerPort inner; // violation

    private final Outer.InnerPort innerPort;

    Declarations(PaymentService payments, PaymentService paymentService) { // violation
        this.users = null;
        this.userRepositoryPort = null;
        this.orderServices = null;
        this.orders = null;
        this.qualified = null;
        this.qualifiedGeneric = null;
        this.qualifiedExact = null;
        this.orderService = null;
        this.inner = null;
        this.innerPort = null;
    }

    void method(PaymentService service) { // violation
    }

    void locals() {
        PaymentService local = null; // violation
        PaymentService paymentService = null;
        for (PaymentService each : List.<PaymentService>of()) { // violation
        }
        for (PaymentService paymentService2 : List.<PaymentService>of()) { // violation
        }
        try (PaymentService resource = open()) { // violation
        }
        try (PaymentService paymentService3 = open()) { // violation
        }
        try (PaymentService paymentService = open()) {
        }
        Consumer<PaymentService> consumer = (PaymentService typed) -> {}; // violation
        BiFunction<PaymentService, PaymentService, String> pair = (PaymentService paymentService, PaymentService other) -> ""; // violation
    }

    record Holder(UserRepositoryPort repo, UserRepositoryPort userRepositoryPort) {} // violation

    record Pair(UserRepositoryPort first) {} // violation
}
