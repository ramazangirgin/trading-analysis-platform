class Constructors {
    void run() {
        new Random(); // violation
        new java.util.Random(); // violation
        new Random() { // violation
        };
        new Random(7); // violation
        new Random[3];
        new int[3];
        new Other();
    }
}
