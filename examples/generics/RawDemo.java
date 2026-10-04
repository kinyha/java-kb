import java.util.ArrayList;
import java.util.List;

// Запуск: java RawDemo.java
// Сырой List: ошибка записи проходит молча, а падает чтение — далеко от места ошибки.
public class RawDemo {
    record Payment(String id, long amount) {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void importLegacy(List batch) {            // старый код без параметра типа
        batch.add("REFUND:42:500");                     // строка вместо Payment — компилятор молчит
    }

    public static void main(String[] args) {
        List<Payment> batch = new ArrayList<>();
        batch.add(new Payment("p-1", 1000));
        importLegacy(batch);                            // сюда пришёл чужой элемент
        System.out.println("size = " + batch.size() + ", contents = " + batch);
        long total = 0;
        try {
            for (Payment p : batch) total += p.amount(); // ClassCastException здесь, а не в importLegacy
        } catch (ClassCastException e) {
            System.out.println("ClassCastException at the read site: " + e.getMessage().replaceAll(" \\(.*", ""));
        }
    }
}
