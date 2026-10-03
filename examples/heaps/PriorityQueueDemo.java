import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class PriorityQueueDemo {
    record Job(String id, int priority, long sequence) {
        @Override public String toString() { return id; }
    }
    static <T> List<T> drain(PriorityQueue<T> queue) {
        var result = new ArrayList<T>();
        while (!queue.isEmpty()) result.add(queue.poll());
        return result;
    }
    public static void main(String[] args) {
        System.out.println("== FIFO and priority ==");
        var fifo = new ArrayDeque<Integer>(List.of(50, 10, 30));
        var priority = new PriorityQueue<Integer>(List.of(50, 10, 30));
        System.out.println("FIFO first=" + fifo.remove() + " priority first=" + priority.poll());

        System.out.println("== order ==");
        var queue = new PriorityQueue<Integer>();
        for (int value : new int[]{10, 30, 20, 70, 40, 50, 60}) queue.offer(value);
        System.out.println("iteration=" + new ArrayList<>(queue)); // Обход, не последовательность poll.
        System.out.println("peek=" + queue.peek() + " size=" + queue.size());
        System.out.println("polls=" + drain(queue));
        System.out.println("empty peek=" + queue.peek() + " poll=" + queue.poll());

        System.out.println("== equal priority ==");
        Comparator<Job> byPriority = Comparator.comparingInt(Job::priority);
        var jobs = List.of(new Job("A", 1, 0), new Job("B", 1, 1), new Job("C", 1, 2));
        var ties = new PriorityQueue<>(byPriority);
        ties.addAll(jobs);
        System.out.println("size=" + ties.size() + " compare A,B=" + byPriority.compare(jobs.get(0), jobs.get(1)));
        System.out.println("priority only=" + drain(ties)); // Точный порядок — наблюдение JDK 21.
        var stable = new PriorityQueue<>(byPriority.thenComparingLong(Job::sequence));
        stable.addAll(jobs);
        System.out.println("priority+sequence=" + drain(stable));

        System.out.println("== equality and capacity ==");
        var sameRank = new PriorityQueue<>(byPriority);
        sameRank.add(jobs.get(0));
        System.out.println("contains B=" + sameRank.contains(jobs.get(1))
                + " remove B=" + sameRank.remove(jobs.get(1)));
        sameRank.add(jobs.get(0)); // Дубликаты допустимы, это не TreeSet.
        System.out.println("same object twice size=" + sameRank.size());
        var small = new PriorityQueue<Integer>(1); // Начальный резерв, а не верхний предел.
        for (int value = 0; value < 20; value++) small.offer(value);
        System.out.println("initialCapacity=1 size=" + small.size());
        try {
            small.offer(null);
        } catch (NullPointerException error) {
            System.out.println("null=" + error.getClass().getSimpleName());
        }
    }
}
