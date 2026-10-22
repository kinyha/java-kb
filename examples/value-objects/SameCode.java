import java.lang.management.ManagementFactory;
import java.time.LocalDate;
import javax.management.ObjectName;

// Один исходник без новых API. Запускается на JDK 28 дважды: без --enable-preview и с ним.
public class SameCode {
    record Payment(Long userId, LocalDate valueDate) { }        // обычный record: identity-класс

    public static void main(String[] args) throws Exception {
        Payment a = new Payment(100_500L, LocalDate.of(2026, 10, 22));
        Payment b = new Payment(100_500L, LocalDate.of(2026, 10, 21).plusDays(1));

        System.out.println("userId ==    " + (a.userId() == b.userId()));        // Long вне кеша -128..127
        System.out.println("valueDate == " + (a.valueDate() == b.valueDate()));  // одна дата, создана двумя путями
        System.out.println("payment ==   " + (a == b));                          // сам Payment остаётся identity

        // Миллион дат валютирования в массиве: что лежит в heap
        LocalDate[] dates = new LocalDate[1_000_000];
        LocalDate start = LocalDate.of(2020, 1, 1);
        for (int i = 0; i < dates.length; i++) dates[i] = start.plusDays(1 + i % 3650);
        histogram("java.time.LocalDate ", "[Ljava.time.LocalDate;");
        System.out.println(dates[dates.length - 1]);                            // массив жив до замера
    }

    // То же, что jcmd <pid> GC.class_histogram, только изнутри процесса
    static void histogram(String... classes) throws Exception {
        String out = (String) ManagementFactory.getPlatformMBeanServer().invoke(
                new ObjectName("com.sun.management:type=DiagnosticCommand"), "gcClassHistogram",
                new Object[]{null}, new String[]{String[].class.getName()});
        for (String line : out.split("\n"))
            for (String c : classes)
                if (line.contains(" " + c)) {
                    String[] f = line.trim().split("\\s+");                    // num: instances bytes class
                    System.out.printf("%-24s %,10d шт. %,12d байт%n", f[3], Long.parseLong(f[1]), Long.parseLong(f[2]));
                }
    }
}
