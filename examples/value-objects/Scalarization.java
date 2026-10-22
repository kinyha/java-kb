import java.lang.management.ManagementFactory;

// JDK 28: java --enable-preview Scalarization.java
//         java --enable-preview -XX:CompileCommand=quiet -XX:CompileCommand=dontinline,*::plus Scalarization.java
public class Scalarization {
    record IdMoney(long kopecks) {                       // обычный record: identity
        IdMoney plus(IdMoney o) { return new IdMoney(kopecks + o.kopecks); }
    }

    value record Money(long kopecks) {                   // тот же код, но без identity
        Money plus(Money o) { return new Money(kopecks + o.kopecks); }
    }

    static long sumIdentity(int n) {
        IdMoney total = new IdMoney(0);
        for (int i = 0; i < n; i++) total = total.plus(new IdMoney(i));   // total переходит в следующую итерацию
        return total.kopecks();
    }

    static long sumValue(int n) {
        Money total = new Money(0);
        for (int i = 0; i < n; i++) total = total.plus(new Money(i));
        return total.kopecks();
    }

    public static void main(String[] args) {
        // счётчик байтов, которые текущий поток выделил в heap
        var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long sink = 0;
        for (int round = 1; round <= 4; round++) {
            long start = threads.getCurrentThreadAllocatedBytes();
            sink += sumIdentity(1_000_000);
            long identity = threads.getCurrentThreadAllocatedBytes() - start;

            start = threads.getCurrentThreadAllocatedBytes();
            sink += sumValue(1_000_000);
            long value = threads.getCurrentThreadAllocatedBytes() - start;
            System.out.printf("раунд %d: identity %,6d КБ, value %,6d КБ%n", round, identity / 1024, value / 1024);
        }
        System.out.println(sink);
    }
}
