import java.time.LocalDate;

// JDK 28 без preview: предупреждение [identity]. С --release 28 --enable-preview: ошибка компиляции.
public class SyncDate {
    public static void main(String[] args) {
        LocalDate valueDate = LocalDate.of(2026, 10, 22);
        synchronized (valueDate) {                      // статический тип — value-класс
            System.out.println(valueDate);
        }
    }
}
