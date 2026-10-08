import java.util.*;

public class AliasingDemo {
    record BadBatch(List<String> ids) {}
    record Batch(List<String> ids) {
        Batch { ids = List.copyOf(ids); } // snapshot: копирует список, не его элементы
    }
    static final class Item {
        String status = "NEW";
    }
    record ShallowBatch(List<Item> items) {
        ShallowBatch { items = List.copyOf(items); }
    }

    public static void main(String[] args) {
        List<String> input = new ArrayList<>(List.of("p1"));
        BadBatch bad = new BadBatch(input);
        Batch safe = new Batch(input);
        Set<BadBatch> keys = new HashSet<>();
        keys.add(bad);
        System.out.println("before: " + keys.contains(bad));
        input.add("p2"); // меняет компонент bad, а с ним hashCode уже вставленного ключа
        System.out.println("bad=" + bad.ids() + ", safe=" + safe.ids());
        System.out.println("after: " + keys.contains(bad) + ", size=" + keys.size());
        try {
            safe.ids().add("p3");
            throw new AssertionError("snapshot is mutable");
        } catch (UnsupportedOperationException expected) {
            System.out.println("safe accessor: UnsupportedOperationException");
        }
        Item item = new Item();
        ShallowBatch shallow = new ShallowBatch(List.of(item));
        item.status = "PAID"; // copyOf не копировал Item
        System.out.println("element status=" + shallow.items().getFirst().status);
    }
}
