import java.util.*;
import static java.util.stream.Collectors.*;

public class Products {
    record Order(String client, List<String> products) {}

    static Map<String, Set<String>> products(List<Order> orders) {
        return orders.stream().collect(groupingBy(Order::client, TreeMap::new,
                flatMapping(o -> o.products().stream(),
                        toCollection(TreeSet::new)))); // flatMapping после выбора клиента сохраняет пустую группу
    }

    public static void main(String[] args) {
        check(products(List.of(new Order("a", List.of("tea", "milk", "tea")),
                new Order("a", List.of("bread")), new Order("b", List.of()))),
                Map.of("a", Set.of("tea", "milk", "bread"), "b", Set.of()));
        check(products(List.of()), Map.of());
        check(products(List.of(new Order("b", List.of()))), Map.of("b", Set.of()));
        check(products(List.of(new Order("a", List.of("tea")), new Order("b", List.of("tea")))),
                Map.of("a", Set.of("tea"), "b", Set.of("tea")));
    }

    static void check(Object got, Object want) {
        if (!Objects.equals(got, want)) throw new AssertionError("got " + got + ", want " + want);
        System.out.println("OK " + got);
    }
}
