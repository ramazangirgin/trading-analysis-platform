class MemberOfType {
    void run() throws Exception {
        TimeUnit.SECONDS.sleep(1); // violation
        java.util.concurrent.TimeUnit.SECONDS.sleep(1); // violation
        unit.sleep(1);
        TimeUnit.sleep(1);
        Thread.sleep(1);
        other.Units.SECONDS.sleep(1);
        lookup().SECONDS.sleep(1);
    }
}
