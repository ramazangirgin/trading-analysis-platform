package com.example;

import java.time.*;

class QualifiedOnDemand {
    void run() {
        Instant.now(); // violation
        Duration.now();
    }
}
