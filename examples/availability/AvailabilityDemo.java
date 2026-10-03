import java.util.Random;

// Запуск: java AvailabilityDemo.java   (JDK 21, без зависимостей)
public class AvailabilityDemo {
    public static void main(String[] args) {
        System.out.println("== allowed downtime");
        for (double a : new double[] {0.99, 0.999, 0.9995, 0.9999, 0.99999})
            System.out.printf("   %-8s per year %8s, per 30 days %8s%n", pct(a), dur((1 - a) * 365 * 86400), dur((1 - a) * 30 * 86400));

        System.out.println("== serial dependencies, each 99.9%");
        for (int n : new int[] {1, 3, 5, 10})
            System.out.printf("   %2d in a chain: %s%n", n, pct(Math.pow(0.999, n)));

        System.out.println("== two replicas of 99%: independent vs same zone (simulated minutes of a year)");
        simulate();

        System.out.println("== error budget, SLO 99.9% over 30 days");
        double budgetMin = 0.001 * 30 * 24 * 60;
        System.out.printf("   budget: %.1f minutes of full outage (or 0.1%% of requests)%n", budgetMin);
        for (double errorRate : new double[] {0.001, 0.01, 0.0144, 1.0}) {
            double burn = errorRate / 0.001;                         // во сколько раз быстрее нормы
            System.out.printf("   error rate %6s -> burn rate %5.1f, budget gone in %s%n",
                    pct(errorRate), burn, dur(30 * 86400 / burn));
        }
        // Сигнал «будить человека»: за последний час сожжено 2% месячного бюджета — это burn rate 14.4.
        System.out.printf("   burn rate 14.4 for 1 hour uses %.1f%% of the monthly budget%n", 14.4 * 1.0 / (30 * 24) * 100);
    }

    // Каждую минуту года реплика недоступна с вероятностью 1%; зона — с вероятностью 0.5%,
    // и тогда недоступны обе реплики в ней. Отказы держатся по 60 минут, а не одну минуту.
    static void simulate() {
        Random rnd = new Random(1);
        int minutes = 365 * 24 * 60;
        boolean[] a = outages(rnd, minutes, 0.01), b = outages(rnd, minutes, 0.01), zone = outages(rnd, minutes, 0.005);
        int downIndependent = 0, downSameZone = 0, downOne = 0;
        for (int m = 0; m < minutes; m++) {
            if (a[m]) downOne++;
            if (a[m] && b[m]) downIndependent++;
            if ((a[m] && b[m]) || zone[m]) downSameZone++;
        }
        System.out.printf("   one replica:                 %s%n", pct(1 - (double) downOne / minutes));
        System.out.printf("   two, independent failures:   %s  (formula 1 - 0.01^2 = 99.99%%)%n", pct(1 - (double) downIndependent / minutes));
        System.out.printf("   two, same zone (zone 99.5%%): %s%n", pct(1 - (double) downSameZone / minutes));
    }

    // Доля минут в отказе = p; отказы — отрезки по 60 минут.
    static boolean[] outages(Random rnd, int minutes, double p) {
        boolean[] down = new boolean[minutes];
        int incidents = (int) Math.round(minutes * p / 60);
        for (int i = 0; i < incidents; i++) {
            int start = rnd.nextInt(minutes - 60);
            for (int m = start; m < start + 60; m++) down[m] = true;
        }
        return down;
    }

    static String pct(double a) { return String.format("%.4f%%", a * 100).replaceAll("0+%$", "%").replaceAll("\\.%$", "%"); }

    static String dur(double s) {
        if (s >= 86400) return String.format("%.1f d", s / 86400);
        if (s >= 3600) return String.format("%.1f h", s / 3600);
        if (s >= 60) return String.format("%.1f min", s / 60);
        return String.format("%.0f s", s);
    }
}
