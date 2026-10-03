import java.util.concurrent.atomic.LongAdder;

// Запуск: java ContentionDemo.java   (JDK 21, без зависимостей)
// Операция: ~13 нс независимой работы (90 шагов LCG) + общая критическая секция на 10 шагов.
// Останов по volatile-флагу, а не по времени каждой операции: System.nanoTime на этой машине плохо масштабируется.
public class ContentionDemo {
    static volatile boolean running;
    static final Object lock = new Object();
    static long shared;

    public static void main(String[] args) throws Exception {
        int cpus = Runtime.getRuntime().availableProcessors();
        System.out.println("== throughput, ops per second (" + cpus + " CPUs)");
        System.out.println("   threads   no shared state   with lock 10% of work");
        double base1 = 0, base2 = 0;
        for (int t : new int[] {1, 2, 4, 8, 16}) {
            double free = run(t, false), locked = run(t, true);
            if (t == 1) { base1 = free; base2 = locked; }
            System.out.printf("   %7d   %8.1fM (x%4.1f)   %8.1fM (x%4.1f)%n", t, free / 1e6, free / base1, locked / 1e6, locked / base2);
        }
    }

    static double run(int threads, boolean withLock) throws Exception {
        LongAdder ops = new LongAdder();
        Thread[] ts = new Thread[threads];
        running = true;
        for (int i = 0; i < threads; i++) {
            ts[i] = new Thread(() -> {
                long local = 0, n = 0, x = 1;
                while (running) {
                    for (int k = 0; k < 90; k++) x = x * 6364136223846793005L + 1442695040888963407L;   // независимая работа
                    if (withLock) synchronized (lock) { for (int k = 0; k < 10; k++) shared = shared * 31 + x; }
                    else for (int k = 0; k < 10; k++) local = local * 31 + x;
                    n++;
                }
                ops.add(n);
                if (local == 42) System.out.print("");
            });
            ts[i].start();
        }
        Thread.sleep(1000);
        running = false;
        for (Thread t : ts) t.join();
        return ops.sum();
    }
}
