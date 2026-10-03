import java.lang.reflect.Field;
import java.util.*;

public class SequenceDemo {
    static final Field DATA;
    static {
        try {
            DATA = ArrayList.class.getDeclaredField("elementData");
            DATA.setAccessible(true); // Только наблюдение: нужен --add-opens.
        } catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }
    static Object[] slots(ArrayList<?> list) throws IllegalAccessException {
        return (Object[]) DATA.get(list);
    }
    static void report(String name, Runnable operation) {
        try { operation.run(); }
        catch (RuntimeException e) { System.out.println(name + ": " + e.getClass().getSimpleName()); }
    }
    public static void main(String[] args) throws Exception {
        System.out.println("[capacity]");
        // BEGIN capacity
        var defaults = new ArrayList<String>();
        System.out.println("default before add: size=" + defaults.size() + " capacity=" + slots(defaults).length);
        defaults.add("A");
        System.out.println("default after add: size=" + defaults.size() + " capacity=" + slots(defaults).length);
        var reserved = new ArrayList<String>(100); // Сто слотов, пока ни одного элемента.
        System.out.println("reserved: size=" + reserved.size() + " capacity=" + slots(reserved).length);
        report("reserved.get(0)", () -> reserved.get(0));
        // END capacity

        System.out.println("[growth and removal]");
        // BEGIN growth
        var list = new ArrayList<String>(4);
        list.addAll(List.of("A", "B", "C", "D"));
        Object[] old = slots(list);
        System.out.println("before: " + list + " capacity=" + old.length);
        list.add("E"); // Места нет: новое хранилище и копирование ссылок.
        System.out.println("after: " + list + " capacity=" + slots(list).length);
        System.out.println("backing changed=" + (old != slots(list)) + " same first object=" + (old[0] == slots(list)[0]));
        System.out.println("removed=" + list.remove(1)); // Сдвиг C, D, E влево.
        System.out.println("slots after remove=" + Arrays.toString(slots(list)));
        list.clear(); // Удаляет ссылки, но сохраняет ёмкость.
        System.out.println("clear: size=" + list.size() + " capacity=" + slots(list).length);
        list.trimToSize();
        System.out.println("trim: size=" + list.size() + " capacity=" + slots(list).length);
        // END growth

        System.out.println("[integer remove]");
        // BEGIN overload
        List<Integer> ids = new ArrayList<>(List.of(10, 20, 30));
        System.out.println("remove index=" + ids.remove(1) + " list=" + ids);
        System.out.println("remove value=" + ids.remove(Integer.valueOf(10)) + " list=" + ids);
        // END overload

        System.out.println("[linked cursor]");
        // BEGIN cursor
        var linked = new LinkedList<>(List.of("A", "B", "C"));
        var cursor = linked.listIterator();
        cursor.next(); // Прошли A; курсор между A и B.
        cursor.add("X"); // Пересвязываем соседей; курсор теперь перед B.
        System.out.println("next=" + cursor.next());
        cursor.remove(); // Удаляем именно элемент, возвращённый next: B.
        System.out.println("list=" + linked + " next=" + cursor.next());
        // END cursor

        System.out.println("[ends]");
        // BEGIN ends
        Deque<String> fifo = new ArrayDeque<>();
        fifo.addLast("A"); fifo.addLast("B"); fifo.addLast("C");
        System.out.println("queue=" + fifo.pollFirst() + "," + fifo.pollFirst() + "," + fifo.pollFirst());
        Deque<String> lifo = new ArrayDeque<>();
        lifo.push("A"); lifo.push("B"); lifo.push("C"); // push == addFirst.
        System.out.println("stack=" + lifo.pop() + "," + lifo.pop() + "," + lifo.pop());
        System.out.println("empty poll=" + fifo.pollFirst());
        report("empty remove", fifo::removeFirst);
        report("offer null", () -> fifo.offerLast(null));
        Deque<String> nullable = new LinkedList<>();
        nullable.addLast(null);
        System.out.println("linked poll=" + nullable.pollFirst() + " size=" + nullable.size());
        // END ends

        System.out.println("[view and iterator]");
        // BEGIN views
        var source = new ArrayList<>(List.of("A", "B", "C", "D"));
        var page = source.subList(1, 3);
        page.clear(); // Это представление: убираем B и C из исходного списка.
        System.out.println("source after page.clear=" + source);
        source.add("E"); // Структурное изменение вне представления.
        report("page.size", page::size);
        var iterator = source.iterator();
        source.add("F");
        report("iterator.next", iterator::next);
        // END views
    }
}
