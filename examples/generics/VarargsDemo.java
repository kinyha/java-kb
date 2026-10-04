import java.util.ArrayList;
import java.util.List;

// Запуск: java VarargsDemo.java
// Generic varargs создаёт массив стёртого типа: его можно «отравить», и ошибка всплывёт позже.
public class VarargsDemo {
    @SafeVarargs   // ложное обещание: метод пишет в массив varargs
    static void poison(List<String>... lists) {
        Object[] array = lists;                 // List<String>[] — на деле List[]
        array[0] = List.of(42);                 // ArrayStoreException не будет: тип элемента — List
    }

    @SafeVarargs   // честное обещание: только читаем
    static <T> List<T> flatten(List<? extends T>... lists) {
        List<T> out = new ArrayList<>();
        for (List<? extends T> l : lists) out.addAll(l);
        return out;
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        List<String> ids = new ArrayList<>(List.of("p-1"));
        List<String>[] holder = new List[] { ids };
        poison(holder);
        try {
            String first = holder[0].get(0);    // checkcast String, вставленный компилятором
            System.out.println(first);
        } catch (ClassCastException e) {
            System.out.println("heap pollution: ClassCastException on read, far from poison()");
        }
        System.out.println("flatten = " + flatten(List.of("p-1"), List.of("p-2", "p-3")));
    }
}
