import java.util.ArrayList;
import java.util.List;

public class VarSwitchDemo {
    enum Route { CARD, SBP }
    static String kind(List<String> value) { return "List"; }
    static String kind(ArrayList<String> value) { return "ArrayList"; }
    static String receipt(Route route, long cents) {
        long fee = switch (route) { // Java 14: each successful branch produces a value
            case CARD -> {
                long calculated = cents * 15 / 1000;
                yield calculated;
            }
            case SBP -> 0;
        };
        return """
                route=%s
                fee=%d
                """.formatted(route, fee).strip(); // Java 15: ordinary String, not a template engine
    }
    public static void main(String[] args) {
        var inferred = new ArrayList<String>(); // static type is ArrayList<String>, not List<String>
        List<String> explicit = inferred; // same object, different compile-time type
        String overloads = kind(inferred) + " / " + kind(explicit);
        if (!overloads.equals("ArrayList / List")) throw new AssertionError(overloads);
        System.out.println("overload=" + overloads);
        var diamond = new ArrayList<>(); // no target type: ArrayList<Object>
        diamond.add(10);
        diamond.add("note");
        System.out.println("diamond=" + diamond);
        String receipt = receipt(Route.CARD, 1000).replace('\n', '|');
        if (!receipt.equals("route=CARD|fee=15")) throw new AssertionError(receipt);
        System.out.println("receipt=" + receipt);
    }
}
