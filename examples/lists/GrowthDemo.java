import java.lang.reflect.Field;
import java.util.*;

// Запуск: java --add-opens java.base/java.util=ALL-UNNAMED GrowthDemo.java   (JDK 21)
// --add-opens нужен только чтобы заглянуть в длину внутреннего массива elementData.
public class GrowthDemo {
    static final Field DATA;
    static { try { DATA = ArrayList.class.getDeclaredField("elementData"); DATA.setAccessible(true); } catch (Exception e) { throw new RuntimeException(e); } }
    static int capacity(ArrayList<?> l) { try { return ((Object[]) DATA.get(l)).length; } catch (IllegalAccessException e) { throw new RuntimeException(e); } }

    public static void main(String[] args) {
        System.out.println("== capacity of new ArrayList<>() while adding");
        ArrayList<String> ids = new ArrayList<>();
        StringBuilder trace = new StringBuilder("size 0 -> cap " + capacity(ids));
        int last = capacity(ids);
        for (int i = 1; i <= 100; i++) {
            ids.add("p-" + i);
            if (capacity(ids) != last) { last = capacity(ids); trace.append(", size ").append(i).append(" -> cap ").append(last); }
        }
        System.out.println(trace);

        System.out.println("== cost of growth for 1 000 000 adds");
        ArrayList<Integer> big = new ArrayList<>();
        long copies = 0, copied = 0; int cap = capacity(big);
        for (int i = 0; i < 1_000_000; i++) {
            int before = cap;
            big.add(i);
            cap = capacity(big);
            if (cap != before) { copies++; copied += before; }   // при росте копируется весь старый массив
        }
        System.out.printf("reallocations: %d, elements copied: %,d (%.2f per add), final capacity: %,d%n",
                copies, copied, (double) copied / 1_000_000, cap);
        ArrayList<Integer> sized = new ArrayList<>(1_000_000);
        for (int i = 0; i < 1_000_000; i++) sized.add(i);
        System.out.printf("new ArrayList<>(1_000_000): capacity %,d, no reallocations%n", capacity(sized));

        System.out.println("== removing does not shrink");
        big.subList(10, big.size()).clear();
        System.out.printf("after clearing all but 10: size %d, capacity %,d%n", big.size(), capacity(big));
        big.trimToSize();
        System.out.printf("after trimToSize(): capacity %d%n", capacity(big));
    }
}
