import java.util.Arrays;
import java.util.Random;

// Запуск: java TailLatencyDemo.java   (JDK 21)
// Моделирование: у каждого сервиса задержка обычно 20 мс, но в 1% случаев — 300 мс,
// и доступность 99,9%. Запрос пользователя проходит цепочку или веер вызовов.
public class TailLatencyDemo {
    static final Random rnd = new Random(7);

    static double callMs() { return rnd.nextDouble() < 0.01 ? 300 : 15 + rnd.nextDouble() * 10; }
    static boolean callOk() { return rnd.nextDouble() < 0.999; }

    public static void main(String[] args) {
        int requests = 200_000;
        System.out.println("== one service: p50 / p99 latency");
        report("1 call", requests, 1, false);
        System.out.println("== sequential chain (each service calls the next)");
        for (int n : new int[] {3, 5, 10}) report(n + " in a chain", requests, n, false);
        System.out.println("== parallel fan-out (wait for all)");
        for (int n : new int[] {5, 10, 50}) report(n + " in parallel", requests, n, true);
        System.out.println("== fan-out of 50 with a hedged request after 40 ms");
        hedged(requests, 50, 40);
    }

    // Hedging: если ответа нет за hedgeMs, шлём копию запроса в другую реплику
    // и берём первый ответ. Копия стартует позже на hedgeMs.
    static void hedged(int requests, int services, double hedgeMs) {
        double[] lat = new double[requests];
        long extra = 0;
        for (int r = 0; r < requests; r++) {
            double max = 0;
            for (int s = 0; s < services; s++) {
                double ms = callMs();
                if (ms > hedgeMs) { extra++; ms = Math.min(ms, hedgeMs + callMs()); }
                max = Math.max(max, ms);
            }
            lat[r] = max;
        }
        Arrays.sort(lat);
        double slow = Arrays.stream(lat).filter(x -> x >= 300).count() * 100.0 / requests;
        System.out.printf("   %-16s p50 %4.0f ms, p99 %4.0f ms, requests hit a 300-ms tail %5.1f%%, extra calls %.1f%%%n",
                "50 hedged", lat[requests / 2], lat[(int) (requests * 0.99)], slow, extra * 100.0 / requests / services);
    }

    static void report(String title, int requests, int services, boolean parallel) {
        double[] lat = new double[requests];
        int failed = 0;
        for (int r = 0; r < requests; r++) {
            double total = 0, max = 0;
            boolean ok = true;
            for (int s = 0; s < services; s++) {
                double ms = callMs();
                total += ms;
                max = Math.max(max, ms);
                ok &= callOk();
            }
            lat[r] = parallel ? max : total;
            if (!ok) failed++;
        }
        Arrays.sort(lat);
        double slow = Arrays.stream(lat).filter(x -> x >= 300).count() * 100.0 / requests;
        System.out.printf("   %-16s p50 %4.0f ms, p99 %4.0f ms, requests hit a 300-ms tail %5.1f%%, availability %.2f%%%n",
                title, lat[requests / 2], lat[(int) (requests * 0.99)], slow, 100.0 - failed * 100.0 / requests);
    }
}
