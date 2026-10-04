import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Запуск: java ErasureDemo.java
public class ErasureDemo {
    record Payment(String id, long amount) {}

    // Поле и метод с параметризованными типами: в байткоде их тип стёрт, но сигнатура сохранена.
    static List<Payment> pending = new ArrayList<>();
    static List<Payment> pending() { return pending; }

    // Реализация Comparable<T> порождает мост-метод compareTo(Object).
    record Amount(long minor) implements Comparable<Amount> {
        public int compareTo(Amount o) { return Long.compare(minor, o.minor); }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void main(String[] args) throws Exception {
        List<String> strings = new ArrayList<>();
        List<Payment> payments = new ArrayList<>();
        System.out.println("same class at runtime: " + (strings.getClass() == payments.getClass()));

        // Параметр типа объекта не хранится, а объявление поля и метода — хранится (атрибут Signature).
        var field = ErasureDemo.class.getDeclaredField("pending");
        System.out.println("field type:         " + field.getType().getName());
        System.out.println("field generic type: " + field.getGenericType());
        var arg = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
        System.out.println("type argument:      " + arg.getTypeName());

        var methods = java.util.Arrays.stream(Amount.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("compareTo"))
                .sorted(Comparator.comparing(Method::isBridge)).toList();   // порядок getDeclaredMethods не гарантирован
        for (Method m : methods)
                System.out.printf("compareTo(%s) bridge=%s synthetic=%s%n",
                        m.getParameterTypes()[0].getSimpleName(), m.isBridge(), m.isSynthetic());

        // Мост-метод делает приведение: чужой объект через сырой тип падает в нём.
        Comparable raw = new Amount(5);
        try { raw.compareTo("five"); }
        catch (ClassCastException e) { System.out.println("raw compareTo(\"five\"): ClassCastException in the bridge"); }

        // Массивы ковариантны и проверяют запись при выполнении; списки инвариантны — проверка при компиляции.
        Object[] objects = new Payment[1];
        try { objects[0] = "not a payment"; }
        catch (ArrayStoreException e) { System.out.println("Object[] = new Payment[1]; store String: ArrayStoreException"); }
        // List<Object> list = new ArrayList<Payment>();   // не компилируется: List<Payment> — не List<Object>

        // PECS: Comparator<? super T> позволяет сортировать Payment компаратором по Object.
        Comparator<Object> byText = Comparator.comparing(Object::toString);
        List<Payment> list = new ArrayList<>(List.of(new Payment("p-2", 1), new Payment("p-1", 2)));
        list.sort(byText);
        System.out.println("sorted with Comparator<Object>: " + list.stream().map(Payment::id).toList());
    }
}
