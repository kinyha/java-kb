import java.time.LocalDate;
import java.time.LocalDateTime;

// JDK 28: java --enable-preview -XX:+UnlockDiagnosticVMOptions -XX:+PrintFieldLayout OrderLayout.java
public class OrderLayout {
    static class Order {
        Integer quantity;
        Long total;
        LocalDate deliveryDate;
        LocalDateTime createdAt;
        Comparable<LocalDate> due;
    }

    public static void main(String[] args) {
        System.out.println(new Order().total);
    }
}
