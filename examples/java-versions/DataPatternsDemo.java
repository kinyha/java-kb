import java.util.ArrayList;
import java.util.List;

public class DataPatternsDemo {
    sealed interface Result permits Sent, SafeSent, Failed {}
    record Sent(List<String> ids) implements Result {} // final reference, mutable object still shared
    record SafeSent(List<String> ids) implements Result {
        SafeSent { ids = List.copyOf(ids); } // defensive snapshot, elements are immutable Strings
    }
    record Failed(String reason) implements Result {}
    static String describe(Result result) {
        return switch (result) { // Java 21: exhaustive type patterns and record deconstruction
            case Sent(var ids) -> "sent=" + ids.size();
            case SafeSent(var ids) -> "safe=" + ids.size();
            case Failed(var reason) -> "failed=" + reason;
        };
    }
    public static void main(String[] args) {
        var ids = new ArrayList<>(List.of("P1"));
        Result shared = new Sent(ids);
        Result snapshot = new SafeSent(ids);
        ids.add("P2"); // record's implicit final does not freeze ids
        String got = describe(shared) + "; " + describe(snapshot);
        if (!got.equals("sent=2; safe=1")) throw new AssertionError(got);
        System.out.println(got);
        System.out.println(describe(new Failed("timeout")));
    }
}
