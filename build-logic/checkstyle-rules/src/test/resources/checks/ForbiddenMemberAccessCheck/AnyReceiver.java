class AnyReceiver {
    void run(Exception e) {
        e.printStackTrace(); // violation
        this.printStackTrace(); // violation
        failure().printStackTrace(); // violation
        failure().cause.printStackTrace(); // violation
        e.printStackTrace(System.out); // violation: the field, not the call
        e.getMessage();
        printStackTrace();
    }
}
