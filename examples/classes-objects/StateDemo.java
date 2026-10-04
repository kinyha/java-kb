import java.util.*;
import java.util.concurrent.*;

// Запуск: java StateDemo.java   (JDK 21, без зависимостей)
public class StateDemo {
    static class Payment {
        long amount;
        Payment(long amount) { this.amount = amount; }
    }

    static void applyFee(Payment p) { p.amount += 15; }          // меняет объект по копии ссылки
    static void replace(Payment p)  { p = new Payment(0); }       // меняет только локальную копию ссылки

    // Статическое поле одно на класс: его делят все экземпляры и все потоки.
    static class FeeCalculator {
        static long lastFee;                                       // «временная» переменная, вынесенная в static
        long fee(long amount) {
            lastFee = amount / 100;
            Thread.onSpinWait();
            return lastFee;
        }
    }

    static class Batch {
        private final List<String> ids = new ArrayList<>();        // final — ссылка, а не содержимое
        List<String> ids() { return ids; }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== references are passed by value");
        Payment p = new Payment(1000);
        applyFee(p);
        System.out.println("after applyFee: " + p.amount);
        replace(p);
        System.out.println("after replace:  " + p.amount);

        System.out.println("== static field shared by all instances and threads");
        int threads = 8, perThread = 200_000;
        var wrong = new java.util.concurrent.atomic.AtomicLong();
        var pool = Executors.newFixedThreadPool(threads);
        var done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            long amount = (t + 1) * 10_000L;                       // у каждого потока своя сумма
            pool.execute(() -> {
                var calc = new FeeCalculator();                    // отдельный экземпляр не помогает
                for (int i = 0; i < perThread; i++)
                    if (calc.fee(amount) != amount / 100) wrong.incrementAndGet();
                done.countDown();
            });
        }
        done.await();
        pool.shutdown();
        System.out.println("wrong fees: " + (wrong.get() > 0 ? "> 0" : "0") + " out of " + threads * perThread);

        System.out.println("== final reference, mutable contents");
        Batch b = new Batch();
        b.ids().add("p-1");
        b.ids().add("p-2");
        System.out.println("final list changed from outside: " + b.ids());
    }
}
