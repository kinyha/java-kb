import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrentReviewDemo {
    static void unsafe(ConcurrentMap<String, Boolean> done, AtomicInteger charged,
                       CyclicBarrier bothRead) throws Exception {
        boolean existed = done.containsKey("k7"); // обе проверки пройдут до записи
        bothRead.await(5, TimeUnit.SECONDS);      // фиксируем нужное чередование
        if (!existed) {
            done.put("k7", true);
            charged.incrementAndGet();           // каждый поток создаёт эффект
        }
    }

    static void atomicClaim(ConcurrentMap<String, Boolean> done, AtomicInteger charged,
                            CyclicBarrier start) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        if (done.putIfAbsent("k7", true) == null) { // один победитель внутри одной карты
            charged.incrementAndGet();
        }
    }

    static int race(boolean atomic) throws Exception {
        ConcurrentMap<String, Boolean> done = new ConcurrentHashMap<>();
        AtomicInteger charged = new AtomicInteger();
        CyclicBarrier gate = new CyclicBarrier(2);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Callable<Void> task = () -> {
                if (atomic) atomicClaim(done, charged, gate);
                else unsafe(done, charged, gate);
                return null;
            };
            Future<Void> first = pool.submit(task);
            Future<Void> second = pool.submit(task);
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
        return charged.get();
    }

    static int sequential() {
        ConcurrentMap<String, Boolean> done = new ConcurrentHashMap<>();
        AtomicInteger charged = new AtomicInteger();
        for (int i = 0; i < 2; i++) {
            if (!done.containsKey("k7")) {
                done.put("k7", true);
                charged.incrementAndGet();
            }
        }
        return charged.get();
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== thread-safe map, compound decision ==");
        System.out.println("sequential effects=" + sequential());
        System.out.println("two reads before write=" + race(false));
        System.out.println("atomic claim effects=" + race(true));
    }
}
