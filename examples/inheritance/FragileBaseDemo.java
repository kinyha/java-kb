import java.util.*;
import java.util.function.UnaryOperator;

// Запуск: java FragileBaseDemo.java   (JDK 21, без зависимостей)
public class FragileBaseDemo {
    // Наследник считает, сколько платежей добавили за всё время.
    static class CountingSet extends HashSet<String> {
        int added;
        @Override public boolean add(String id) { added++; return super.add(id); }
        @Override public boolean addAll(Collection<? extends String> ids) { added += ids.size(); return super.addAll(ids); }
    }

    // Композиция: свой класс, внутри — Set, наружу — только интерфейс.
    static class CountingSetComposed extends AbstractSet<String> {
        private final Set<String> inner = new HashSet<>();
        int added;
        @Override public boolean add(String id) { added++; return inner.add(id); }
        @Override public boolean addAll(Collection<? extends String> ids) { added += ids.size(); return inner.addAll(ids); }
        @Override public Iterator<String> iterator() { return inner.iterator(); }
        @Override public int size() { return inner.size(); }
    }

    // Список, который «не допускает пустых идентификаторов»: закрыли add и set.
    static class NonBlankList extends ArrayList<String> {
        static String check(String id) {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("blank id");
            return id;
        }
        @Override public boolean add(String id) { return super.add(check(id)); }
        @Override public void add(int i, String id) { super.add(i, check(id)); }
        @Override public String set(int i, String id) { return super.set(i, check(id)); }
    }

    public static void main(String[] args) {
        System.out.println("== self-use inside HashSet.addAll");
        CountingSet s = new CountingSet();
        s.addAll(List.of("p-1", "p-2", "p-3"));
        System.out.println("extends HashSet: added = " + s.added + " (expected 3)");
        CountingSetComposed c = new CountingSetComposed();
        c.addAll(List.of("p-1", "p-2", "p-3"));
        System.out.println("composition:     added = " + c.added);

        System.out.println("== base class grows methods the subclass did not override");
        NonBlankList ids = new NonBlankList();
        ids.add("p-1");
        try { ids.add(" "); } catch (IllegalArgumentException e) { System.out.println("add(\" \"): rejected"); }
        ids.replaceAll(id -> "");                                   // Java 8: ArrayList.replaceAll не вызывает set
        ids.addAll(Arrays.asList("p-2", null));                     // addAll не переопределён
        System.out.println("after replaceAll and addAll: " + ids);

        System.out.println("== JDK's own mistakes: Stack extends Vector, Properties extends Hashtable");
        Stack<String> stack = new Stack<>();
        stack.push("p-1"); stack.push("p-2"); stack.push("p-3");
        stack.add(0, "p-0");                                        // вставка в дно стека
        stack.remove(2);                                            // удаление из середины
        System.out.println("stack after add(0)/remove(2): " + stack + ", pop = " + stack.pop());
        Properties props = new Properties();
        props.put("timeout", 30);                                   // Hashtable.put принимает Object
        System.out.println("getProperty(\"timeout\") = " + props.getProperty("timeout") + ", get = " + props.get("timeout"));
    }
}
