import java.lang.ref.WeakReference;
import java.time.LocalDate;
import java.util.Map;
import java.util.WeakHashMap;

// JDK 21 без preview. Код, который сломается, когда LocalDate и Integer станут value-классами.
public class Prepare {
    static final Map<LocalDate, String> holidays = new WeakHashMap<>();   // слабые ключи-даты

    public static void main(String[] args) {
        Object lock = Integer.valueOf(1996);              // статический тип Object: javac не предупредит
        synchronized (lock) {
            var ref = new WeakReference<>(LocalDate.of(2026, 10, 22));
            System.out.println("locked, ref alive: " + (ref.get() != null) + ", holidays: " + holidays.size());
        }
    }
}
