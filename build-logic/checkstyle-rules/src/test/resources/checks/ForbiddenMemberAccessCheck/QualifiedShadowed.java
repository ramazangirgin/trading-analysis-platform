import com.example.System;

class QualifiedShadowed {
    void run() {
        System.out.println();
        java.lang.System.out.println(); // violation
    }
}
