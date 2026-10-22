// Код времён Java 8: освобождение ресурсов через finalize и «боксинг» конструктором.
public class ReportCache {
    private final Integer limit = new Integer(100);      // forRemoval с JDK 16: используй Integer.valueOf
    private long handle = 42;

    @Override
    protected void finalize() {                          // finalization: forRemoval с JDK 18 (JEP 421)
        handle = 0;
    }

    public static void main(String[] args) {
        Runtime.getRuntime().runFinalization();          // forRemoval с JDK 18
        System.out.println("limit=" + new ReportCache().limit + ", thread=" + Thread.currentThread().getId());
    }
}
