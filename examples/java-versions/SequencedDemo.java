import java.util.ArrayList;
import java.util.List;

public class SequencedDemo {
    public static void main(String[] args) {
        var original = new ArrayList<>(List.of("P1", "P2"));
        var snapshot = List.copyOf(original);
        var reverse = original.reversed(); // Java 21: a view over the same data
        reverse.addFirst("P3"); // first in reverse order is last in original order
        if (!original.equals(List.of("P1", "P2", "P3"))) throw new AssertionError();
        System.out.println("original=" + original);
        System.out.println("reverse=" + reverse);
        System.out.println("snapshot=" + snapshot);
        try {
            snapshot.addFirst("P4"); // new API does not make an immutable list mutable
            throw new AssertionError("mutation accepted");
        } catch (UnsupportedOperationException expected) {
            System.out.println("snapshot mutation=" + expected.getClass().getSimpleName());
        }
    }
}
