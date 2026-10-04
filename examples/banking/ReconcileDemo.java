import java.util.*;

public class ReconcileDemo {
    record Row(String ref, String currency, long outgoing) {}
    static Map<String, Row> unique(List<Row> rows) {
        Map<String, Row> byRef = new TreeMap<>();
        for (Row row : rows)
            if (byRef.putIfAbsent(row.ref(), row) != null)
                throw new IllegalArgumentException("duplicate " + row.ref());
        return byRef; // Проверяем дубликаты до сопоставления; не затираем их.
    }
    static void reconcile(List<Row> ours, List<Row> bank) {
        Map<String, Row> a = unique(ours), b = unique(bank);
        Set<String> ids = new TreeSet<>(a.keySet()); ids.addAll(b.keySet());
        for (String id : ids) {
            if (!a.containsKey(id)) System.out.println("extra-external=" + id);
            else if (!b.containsKey(id)) System.out.println("missing-external=" + id);
            else if (!a.get(id).equals(b.get(id))) System.out.println("different=" + id);
        }
    }
    static long total(List<Row> rows) {
        return rows.stream().mapToLong(Row::outgoing).reduce(0, Math::addExact);
    }
    public static void main(String[] args) {
        List<Row> ours = List.of(new Row("p1", "RUB", 10_000), new Row("p2", "RUB", 20_000));
        List<Row> bank = List.of(new Row("p1", "RUB", 10_000), new Row("p3", "RUB", 20_000));
        System.out.printf("totals ours=%d bank=%d%n", total(ours), total(bank));
        reconcile(ours, bank); // Суммы совпали, идентичность операций — нет.
        reconcile(List.of(new Row("p1", "RUB", 10_000)), List.of(new Row("p1", "RUB", 9_900)));
        try { reconcile(ours, List.of(bank.get(0), bank.get(0))); }
        catch (IllegalArgumentException e) { System.out.println("rejected=" + e.getMessage()); }
    }
}
