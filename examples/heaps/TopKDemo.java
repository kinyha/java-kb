import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;

public class TopKDemo {
    public static void main(String[] args) {
        int k = 3;
        var largest = new PriorityQueue<Integer>(); // Корень — худшая из сохранённых сумм.
        for (int amount : new int[]{40, 10, 70, 20, 60, 90, 50}) {
            if (largest.size() < k) largest.offer(amount);
            else if (amount > largest.peek()) {
                largest.poll();
                largest.offer(amount);
            }
            System.out.println("amount=" + amount + " kept=" + largest + " cutoff=" + largest.peek());
        }
        var result = new ArrayList<>(largest);
        result.sort(Comparator.reverseOrder()); // Вывод трёх сумм отдельно сортируем.
        System.out.println("top3 descending=" + result);
    }
}
