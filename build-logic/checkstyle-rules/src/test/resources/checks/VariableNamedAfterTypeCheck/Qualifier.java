package demo;

public class Qualifier {

    void qualifiers() {
        PaymentService strictPaymentService = null;
        PaymentService paymentService = null;
        PaymentService PaymentService = null; // violation
        PaymentService Strict_PaymentService = null; // violation
        PaymentService myPaymentServiceCopy = null; // violation
        PaymentService paymentservice = null; // violation
        AIService strictAIService = null;
        AIService aiService = null;
        AIService aIService = null; // violation
        AIService service = null; // violation
        URL url = null;
        URL link = null; // violation
    }
}
