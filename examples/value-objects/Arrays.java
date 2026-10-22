import java.lang.management.ManagementFactory;
import java.time.LocalDate;
import javax.management.ObjectName;

// JDK 28: java --enable-preview Arrays.java
public class Arrays {
    public static void main(String[] args) throws Exception {
        int n = 1_000_000;
        Integer[] fees = new Integer[n];                  // 32 бита + флаг null
        Long[] amounts = new Long[n];                     // 64 бита + флаг null
        LocalDate[] dates = new LocalDate[n];             // 48 бит + флаг null
        Object[] mixed = new Object[n];                   // тип элемента Object: сюда можно положить что угодно
        LocalDate start = LocalDate.of(2020, 1, 1);
        for (int i = 0; i < n; i++) {
            fees[i] = 1_000 + i;
            amounts[i] = 1_000L + i;
            dates[i] = start.plusDays(1 + i % 3650);
            mixed[i] = 1_000 + i;                         // те же Integer, что в fees
        }
        histogram("[Ljava.lang.Integer;", "[Ljava.lang.Long;", "[Ljava.time.LocalDate;",
                  "java.lang.Integer ", "java.lang.Long ", "java.time.LocalDate ");
        System.out.println(fees[7] + amounts[7] + dates[7].getYear() + mixed[7].hashCode());
    }

    static void histogram(String... classes) throws Exception {
        String out = (String) ManagementFactory.getPlatformMBeanServer().invoke(
                new ObjectName("com.sun.management:type=DiagnosticCommand"), "gcClassHistogram",
                new Object[]{null}, new String[]{String[].class.getName()});
        for (String c : classes)
            for (String line : out.split("\n"))
                if (line.contains(" " + c)) {
                    String[] f = line.trim().split("\\s+");
                    if (Long.parseLong(f[2]) < 1_000_000) continue;   // мелкие строки (кеши JDK) пропускаем
                    System.out.printf("%-24s %,10d шт. %,12d байт%n", f[3], Long.parseLong(f[1]), Long.parseLong(f[2]));
                }
    }
}
