package com.example;

import java.util.List;

class QualifiedNoImport {
    void run() {
        Instant.now();
        java.time.Instant.now(); // violation
        time.Instant.now();
    }
}
