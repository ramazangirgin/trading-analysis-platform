package demo;

import java.util.List;
import java.util.function.Consumer;

public class NotChecked {

    static final PaymentService DEFAULT = null;

    static PaymentService shared = null; // violation

    final PaymentService notStatic = null; // violation

    private final OrderLoader loader = null;

    private final PaymentServiceFactory paymentServiceFactory = null;

    private final PaymentServiceFactory factory = null;

    private final PaymentService[] services = null;

    private final PaymentService legacy[] = null;

    private final int count = 0;

    void method(PaymentService... services) {
    }

    void locals() {
        var inferred = open();
        PaymentService _ = null;
        Consumer<PaymentService> consumer = service -> {};
        java.util.function.BiConsumer<String, String> pair = (first, second) -> {};
        for (var each : List.of()) {
        }
        try (var resource = open()) {
        }
    }

    void existingResource(java.io.InputStream in) throws Exception {
        try (in) {
        }
    }
}
