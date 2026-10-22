import java.time.LocalDate;
import java.time.LocalDateTime;

// JDK 28: java --enable-preview -XX:+UnlockDiagnosticVMOptions -XX:+PrintFieldLayout Layout.java
public class Layout {
    value record Fee(int kopecks) { }                     // 32 бита данных
    value record Money(long kopecks) { }                  // 64 бита данных
    value record Rate(int bps, short currency) { }        // 48 бит данных

    static class Payment {                                // identity-класс: поля можно перезаписать
        Fee fee;
        Money amount;
        Rate rate;
        LocalDate valueDate;                              // int + byte + byte = 48 бит
    }

    value record Transfer(Money amount, LocalDateTime at) { }   // value-класс: поля final

    public static void main(String[] args) {
        System.out.println(new Payment().amount + " " + new Transfer(new Money(1), null));
    }
}
