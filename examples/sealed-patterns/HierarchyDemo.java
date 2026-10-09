import java.util.Arrays;

public class HierarchyDemo {
    sealed interface Outcome permits Paid, Rejected, Temporary {}
    record Paid(String id) implements Outcome {}
    record Rejected(String reason) implements Outcome {}
    sealed interface Temporary extends Outcome permits Retry, External {}
    record Retry(int seconds) implements Temporary {}
    non-sealed interface External extends Temporary {}
    record ProviderWait(String provider) implements External {}

    static String route(Outcome value) {
        return switch (value) {
            case Paid p -> "paid";
            case Rejected r -> "rejected";
            case Retry r -> "retry";
            case External e -> "external"; // covers every present and future implementation
        };
    }

    public static void main(String[] args) {
        System.out.println("sealed=" + Outcome.class.isSealed());
        System.out.println("direct=" + Arrays.stream(Outcome.class.getPermittedSubclasses())
                .map(Class::getSimpleName).sorted().toList());
        System.out.println("external sealed=" + External.class.isSealed());
        System.out.println("provider=" + route(new ProviderWait("bank")));
    }
}
