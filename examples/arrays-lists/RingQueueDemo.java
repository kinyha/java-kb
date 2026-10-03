import java.util.Arrays;
import java.util.Objects;

public class RingQueueDemo {
    static class Ring {
        final String[] a;
        int head, tail, size;
        Ring(int capacity) {
            if (capacity <= 0) throw new IllegalArgumentException("capacity");
            a = new String[capacity];
        }
        boolean offer(String payment) {
            Objects.requireNonNull(payment);
            if (size == a.length) return false; // Фиксированный лимит очереди.
            a[tail] = payment;
            tail = (tail + 1) % a.length;
            size++;
            return true;
        }
        String poll() {
            if (size == 0) return null;
            String payment = a[head];
            a[head] = null; // Освобождаем ссылку на обработанный платёж.
            head = (head + 1) % a.length;
            size--;
            return payment;
        }
        void show() {
            System.out.println(Arrays.toString(a) + " head=" + head + " tail=" + tail + " size=" + size);
        }
    }
    public static void main(String[] args) {
        Ring q = new Ring(4);
        q.offer("A"); q.offer("B"); q.offer("C"); q.show();
        System.out.println("polled=" + q.poll() + "," + q.poll()); q.show();
        q.offer("D"); q.offer("E"); q.show();
        q.offer("F"); q.show();
        System.out.println("offer G=" + q.offer("G"));
        System.out.println("drain=" + q.poll() + "," + q.poll() + "," + q.poll() + "," + q.poll());
        q.show(); System.out.println("empty=" + q.poll());
    }
}
