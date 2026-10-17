package demo;

import com.example.Other;
import com.example.Required;

class OrderService {
    @Required
    public void first() {}

    @Other
    public void second() {}

    public void third() {} // violation
}

class AuditHandler {
    @Other
    public void second() {}

    public void third() {} // violation
}

class OrderController {
    public void third() {}
}
