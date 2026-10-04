import java.util.ArrayList;
import java.util.List;

// Запуск: java PecsDemo.java
// Producer extends, consumer super: откуда читаем — ? extends, куда пишем — ? super.
public class PecsDemo {
    static class Payment { final long amount; Payment(long a) { amount = a; } public String toString() { return getClass().getSimpleName() + ":" + amount; } }
    static class CardPayment extends Payment { CardPayment(long a) { super(a); } }
    static class SbpPayment extends Payment { SbpPayment(long a) { super(a); } }

    // Источник: читаем как Payment, принимаем список любого подтипа.
    static long total(List<? extends Payment> source) {
        long sum = 0;
        for (Payment p : source) sum += p.amount;
        // source.add(new Payment(1));   // не компилируется: неизвестно, Card это список или Sbp
        return sum;
    }

    // Приёмник: пишем CardPayment, принимаем список любого надтипа.
    static void addCards(List<? super CardPayment> sink, int n) {
        for (int i = 1; i <= n; i++) sink.add(new CardPayment(i * 100));
        // CardPayment c = sink.get(0);  // не компилируется: там может лежать любой Payment или Object
    }

    // Копирование: из производителя T в потребителя T — сигнатура как у Collections.copy.
    static <T> void copy(List<? super T> dst, List<? extends T> src) { dst.addAll(src); }

    public static void main(String[] args) {
        List<CardPayment> cards = new ArrayList<>();
        addCards(cards, 2);
        List<Payment> all = new ArrayList<>();
        addCards(all, 1);                                   // List<Payment> — надтип для CardPayment
        all.add(new SbpPayment(700));
        List<Object> audit = new ArrayList<>();
        copy(audit, all);                                   // T = Payment
        System.out.println("cards = " + cards + ", total = " + total(cards));
        System.out.println("all   = " + all + ", total = " + total(all));
        System.out.println("audit = " + audit);
    }
}
