import java.util.*;

// Запуск: java RemoveDemo.java   (JDK 21, без зависимостей)
public class RemoveDemo {
    public static void main(String[] args) {
        System.out.println("== remove(int) or remove(Object)?");
        List<Integer> amounts = new ArrayList<>(List.of(500, 1, 700, 2));
        amounts.remove(1);                                     // индекс 1, а не значение 1
        System.out.println("remove(1):                  " + amounts);
        amounts.remove(Integer.valueOf(2));                    // значение
        System.out.println("remove(Integer.valueOf(2)): " + amounts);

        System.out.println("== index loop skips neighbours");
        List<String> batch = new ArrayList<>(List.of("refund", "refund", "pay", "refund", "pay"));
        for (int i = 0; i < batch.size(); i++)
            if (batch.get(i).equals("refund")) batch.remove(i);   // следующий элемент сдвинулся на i
        System.out.println("after index loop: " + batch);

        System.out.println("== for-each remove: CME, except on the second-to-last element");
        List<String> a = new ArrayList<>(List.of("p-1", "p-2", "p-3", "p-4"));
        try { for (String id : a) if (id.equals("p-2")) a.remove(id); }
        catch (ConcurrentModificationException e) { System.out.println("remove p-2: ConcurrentModificationException"); }
        List<String> b = new ArrayList<>(List.of("p-1", "p-2", "p-3", "p-4"));
        for (String id : b) if (id.equals("p-3")) b.remove(id);   // предпоследний: hasNext() вернул false
        System.out.println("remove p-3: no exception, list = " + b);

        System.out.println("== removing half: iterator.remove() vs removeIf");
        int n = 200_000;
        for (int round = 0; round < 3; round++) {                  // прогрев: последний раунд печатается
            List<Integer> l1 = new ArrayList<>(); for (int i = 0; i < n; i++) l1.add(i);
            long t0 = System.nanoTime();
            Iterator<Integer> it = l1.iterator();
            while (it.hasNext()) if (it.next() % 2 == 0) it.remove();   // каждый remove сдвигает хвост
            long t1 = System.nanoTime();
            List<Integer> l2 = new ArrayList<>(); for (int i = 0; i < n; i++) l2.add(i);
            long t2 = System.nanoTime();
            l2.removeIf(x -> x % 2 == 0);                               // один проход и одно сжатие
            long t3 = System.nanoTime();
            if (round == 2) System.out.printf("iterator.remove(): %d ms, removeIf: %.1f ms, same result: %b%n",
                    (t1 - t0) / 1_000_000, (t3 - t2) / 1e6, l1.equals(l2));
        }
    }
}
