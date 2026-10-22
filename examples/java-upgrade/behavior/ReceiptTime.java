import java.nio.charset.Charset;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

public class ReceiptTime {
    public static void main(String[] args) {
        DateTimeFormatter fmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.US);
        String time = fmt.format(LocalTime.of(15, 30));
        char gap = time.charAt(time.length() - 3);              // символ между "3:30" и "PM"
        System.out.printf("time=%s gap=U+%04X%n", time, (int) gap);
        try {
            LocalTime.parse("3:30 PM", fmt);                    // строка с обычным пробелом, как в старых тестах и клиентах
            System.out.println("parse '3:30 PM': OK");
        } catch (Exception e) {
            System.out.println("parse '3:30 PM': " + e.getClass().getSimpleName());
        }
        System.out.println("default charset: " + Charset.defaultCharset());
    }
}
