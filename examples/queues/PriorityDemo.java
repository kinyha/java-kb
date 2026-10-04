import java.time.LocalTime;
import java.util.*;

// Запуск: java PriorityDemo.java   (JDK 21, без зависимостей)
public class PriorityDemo {
    static final class Retry {
        final String id; LocalTime at;
        Retry(String id, LocalTime at) { this.id = id; this.at = at; }
        @Override public String toString() { return id + "@" + at; }
    }

    public static void main(String[] args) {
        System.out.println("== PriorityQueue: heap order is not sorted order");
        PriorityQueue<Integer> pq = new PriorityQueue<>(List.of(70, 20, 50, 10, 40, 30, 60));
        System.out.println("toString / iteration: " + pq);
        StringBuilder polled = new StringBuilder();
        while (!pq.isEmpty()) polled.append(pq.poll()).append(' ');
        System.out.println("poll one by one:      " + polled.toString().trim());

        System.out.println("== retries by time, earliest first");
        PriorityQueue<Retry> retries = new PriorityQueue<>(Comparator.comparing((Retry r) -> r.at));
        Retry a = new Retry("p-1", LocalTime.of(10, 5)), b = new Retry("p-2", LocalTime.of(10, 1)), c = new Retry("p-3", LocalTime.of(10, 3));
        retries.addAll(List.of(a, b, c));
        System.out.println("peek = " + retries.peek());
        a.at = LocalTime.of(10, 0);                      // изменили приоритет элемента внутри очереди
        System.out.println("after p-1 moved to 10:00, peek = " + retries.peek() + " (heap did not notice)");
        retries.remove(a); retries.add(a);               // правильно: удалить и вставить заново
        System.out.println("after remove + add,      peek = " + retries.peek());

        System.out.println("== push/pop 10 000 000 times");
        for (int round = 0; round < 3; round++) {
            Deque<Integer> ad = new ArrayDeque<>(); Deque<Integer> ll = new LinkedList<>(); Stack<Integer> st = new Stack<>();
            Integer v = 1; long s = 0;
            long t0 = System.nanoTime();
            for (int i = 0; i < 10_000_000; i++) { ad.push(v); if ((i & 3) == 3) { s += ad.pop(); s += ad.pop(); } }
            long t1 = System.nanoTime();
            for (int i = 0; i < 10_000_000; i++) { ll.push(v); if ((i & 3) == 3) { s += ll.pop(); s += ll.pop(); } }
            long t2 = System.nanoTime();
            for (int i = 0; i < 10_000_000; i++) { st.push(v); if ((i & 3) == 3) { s += st.pop(); s += st.pop(); } }
            long t3 = System.nanoTime();
            if (round == 2) System.out.printf("ArrayDeque %d ms, LinkedList %d ms, Stack %d ms (%d)%n",
                    (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, (t3 - t2) / 1_000_000, s);
        }
    }
}
