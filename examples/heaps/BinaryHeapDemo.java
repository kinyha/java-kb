import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public class BinaryHeapDemo {
    static final class Heap {
        final int[] a;
        int size;
        long comparisons;
        boolean trace;
        Heap(int capacity) { a = new int[capacity]; }
        Heap(int[] values) {
            a = Arrays.copyOf(values, Math.max(16, values.length));
            size = values.length;
            heapify();
        }
        boolean less(int x, int y) {
            comparisons++; // Только сравнения значений, не проверка границ.
            return a[x] < a[y];
        }
        void swap(int x, int y) {
            int value = a[x]; a[x] = a[y]; a[y] = value;
        }
        void offer(int value) {
            if (size == a.length) throw new IllegalStateException("heap is full");
            int at = size++;
            a[at] = value; // Новый элемент занимает последнее место дерева.
            if (trace) show("append");
            while (at > 0) {
                int parent = (at - 1) / 2;
                if (!less(at, parent)) break;
                swap(at, parent);
                at = parent;
                if (trace) show("up " + at);
            }
        }
        int peek() {
            if (size == 0) throw new NoSuchElementException();
            return a[0];
        }
        int poll() {
            int result = peek();
            a[0] = a[--size]; // Последний элемент заполняет корень, последний слот покидает кучу.
            if (trace) show("replace last");
            siftDown(0);
            return result;
        }
        void siftDown(int at) {
            while (at < size / 2) {
                int child = 2 * at + 1;
                if (child + 1 < size && less(child + 1, child)) child++;
                if (!less(child, at)) break; // Родитель уже не больше обоих детей.
                swap(at, child);
                at = child;
                if (trace) show("down " + at);
            }
        }
        void heapify() {
            for (int at = size / 2 - 1; at >= 0; at--) siftDown(at);
        }
        boolean valid() {
            for (int at = 1; at < size; at++)
                if (a[(at - 1) / 2] > a[at]) return false;
            return true;
        }
        String snapshot() { return Arrays.toString(Arrays.copyOf(a, size)); }
        void show(String stage) { System.out.println(stage + ": " + snapshot() + " size=" + size); }
    }
    public static void main(String[] args) {
        System.out.println("== offer ==");
        var heap = new Heap(new int[]{10, 30, 20, 70, 40, 50, 60});
        heap.trace = true;
        heap.offer(5);
        System.out.println("== poll ==");
        heap.show("before");
        int first = heap.poll();
        System.out.println("returned=" + first + " valid=" + heap.valid());

        System.out.println("== heapify steps ==");
        var steps = new Heap(7);
        int[] input = {7, 6, 5, 4, 3, 2, 1};
        System.arraycopy(input, 0, steps.a, 0, input.length);
        steps.size = input.length;
        steps.show("raw");
        for (int at = steps.size / 2 - 1; at >= 0; at--) {
            steps.siftDown(at);
            steps.show("root " + at);
        }
        System.out.println("valid=" + steps.valid() + " min=" + steps.peek());

        System.out.println("== build work ==");
        int n = 1023;
        int[] descending = new int[n];
        for (int i = 0; i < n; i++) descending[i] = n - i;
        var bottomUp = new Heap(descending);
        var repeated = new Heap(n);
        for (int value : descending) repeated.offer(value);
        System.out.println("heapify n=" + n + " comparisons=" + bottomUp.comparisons
                + " valid=" + bottomUp.valid() + " min=" + bottomUp.peek());
        System.out.println("offers n=" + n + " comparisons=" + repeated.comparisons
                + " valid=" + repeated.valid() + " min=" + repeated.peek());
        var drained = new ArrayList<Integer>();
        while (steps.size > 0) drained.add(steps.poll());
        System.out.println("poll order=" + drained);
    }
}
