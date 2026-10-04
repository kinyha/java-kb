import java.lang.reflect.Field;
import java.util.*;

// Запуск: java --add-opens java.base/java.util=ALL-UNNAMED DequeDemo.java   (JDK 21)
// --add-opens нужен только чтобы показать массив, head и tail внутри ArrayDeque.
public class DequeDemo {
    static Object get(Object o, String f) throws Exception { Field x = ArrayDeque.class.getDeclaredField(f); x.setAccessible(true); return x.get(o); }
    static String state(ArrayDeque<String> d) throws Exception {
        Object[] a = (Object[]) get(d, "elements");
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) sb.append(i > 0 ? " " : "").append(a[i] == null ? "." : a[i]);
        return sb.append("] head=").append(get(d, "head")).append(" tail=").append(get(d, "tail")).toString();
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== ArrayDeque is a circular array");
        ArrayDeque<String> d = new ArrayDeque<>(6);
        System.out.println("new ArrayDeque<>(6):  " + state(d));
        d.addLast("a"); d.addLast("b"); d.addLast("c");
        System.out.println("addLast a, b, c:      " + state(d));
        d.addFirst("z");
        System.out.println("addFirst z:           " + state(d) + "  -> " + d);
        d.pollFirst(); d.pollFirst();
        System.out.println("pollFirst x2:         " + state(d) + "  -> " + d);
        d.addLast("d"); d.addLast("e"); d.addLast("f");
        System.out.println("addLast d, e, f:      " + state(d) + "  -> " + d);
        d.addLast("g"); d.addLast("h");
        System.out.println("addLast g, h (grow):  " + state(d) + "  -> " + d);

        System.out.println("== Queue API: exceptions or special values");
        Queue<String> empty = new ArrayDeque<>();
        System.out.println("poll() = " + empty.poll() + ", peek() = " + empty.peek());
        try { empty.remove(); } catch (NoSuchElementException e) { System.out.println("remove(): NoSuchElementException"); }
        try { empty.offer(null); } catch (NullPointerException e) { System.out.println("offer(null): NullPointerException"); }

        System.out.println("== stack: ArrayDeque vs Stack");
        Deque<String> deque = new ArrayDeque<>(); Stack<String> stack = new Stack<>();
        for (String s : List.of("p-1", "p-2", "p-3")) { deque.push(s); stack.push(s); }
        System.out.println("ArrayDeque push x3: pop = " + deque.peek() + ", iterates " + deque);
        System.out.println("Stack      push x3: pop = " + stack.peek() + ", iterates " + stack);
        System.out.println("Stack is synchronized Vector: " + (stack instanceof Vector));
    }
}
