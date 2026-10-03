import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class MutablePriorityDemo {
    static final class Job {
        final String id;
        int priority;
        Job(String id, int priority) { this.id = id; this.priority = priority; }
        @Override public String toString() { return id + ":" + priority; }
    }
    static List<Job> drain(PriorityQueue<Job> queue) {
        var result = new ArrayList<Job>();
        while (!queue.isEmpty()) result.add(queue.poll());
        return result;
    }
    public static void main(String[] args) {
        Comparator<Job> byPriority = Comparator.comparingInt(job -> job.priority);
        var a = new Job("A", 5);
        var b = new Job("B", 10);
        var c = new Job("C", 20);
        var broken = new PriorityQueue<>(byPriority);
        broken.addAll(List.of(a, b, c));
        c.priority = 1; // Ссылки и массив остались на прежних местах.
        System.out.println("changed C: peek=" + broken.peek());
        System.out.println("broken polls=" + drain(broken));

        c.priority = 20;
        var fixed = new PriorityQueue<>(byPriority);
        fixed.addAll(List.of(a, b, c));
        fixed.remove(c); // Ищет по equals за O(n); Job здесь использует равенство ссылок.
        c.priority = 1; // Значимое поле меняем уже вне очереди.
        fixed.offer(c); // Вставка восстановит путь до корня.
        System.out.println("reinserted C: peek=" + fixed.peek());
        System.out.println("fixed polls=" + drain(fixed));
    }
}
