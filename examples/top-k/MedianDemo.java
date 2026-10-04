import java.util.*;

public class MedianDemo {
    static final class Median {
        final PriorityQueue<Integer> low = new PriorityQueue<>(Comparator.reverseOrder());
        final PriorityQueue<Integer> high = new PriorityQueue<>();
        void add(int value) {
            if (low.isEmpty() || value <= low.peek()) low.add(value); // Нижняя половина хранит максимум в корне.
            else high.add(value);
            if (low.size() > high.size() + 1) high.add(low.remove());
            else if (low.size() < high.size()) low.add(high.remove()); // Low содержит один лишний при нечётной длине.
        }
        double value() {
            if (low.isEmpty()) throw new NoSuchElementException("empty stream");
            return low.size() == high.size()
                ? ((long) low.peek() + high.peek()) / 2.0 // Расширяем до сложения: int может переполниться.
                : low.peek();
        }
        String state() {
            List<Integer> a = new ArrayList<>(low), b = new ArrayList<>(high);
            a.sort(Comparator.reverseOrder()); Collections.sort(b);
            return "low=" + a + " high=" + b;
        }
    }

    public static void main(String[] args) {
        System.out.println("== two roots delimit the middle ==");
        Median median = new Median();
        for (int value : new int[]{5, 1, 9, 2, 8, 3}) {
            median.add(value);
            System.out.println("add=" + value + " " + median.state() + " median=" + median.value());
        }

        System.out.println("== widening must happen before addition ==");
        Median sameMax = new Median(); sameMax.add(Integer.MAX_VALUE); sameMax.add(Integer.MAX_VALUE);
        System.out.println("same max=" + sameMax.value());
        System.out.println("wrong int average=" + (Integer.MAX_VALUE + Integer.MAX_VALUE) / 2.0);
        Median extremes = new Median(); extremes.add(Integer.MIN_VALUE); extremes.add(Integer.MAX_VALUE);
        System.out.println("mixed extremes=" + extremes.value());

        System.out.println("== median needs at least one value ==");
        try { new Median().value(); }
        catch (NoSuchElementException e) { System.out.println("empty=" + e.getClass().getSimpleName()); }
        Median repeated = new Median(); repeated.add(7); repeated.add(7); repeated.add(7);
        System.out.println("repeated=" + repeated.state() + " median=" + repeated.value());
    }
}
