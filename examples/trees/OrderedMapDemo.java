import java.math.BigDecimal;
import java.util.TreeMap;

public class OrderedMapDemo {
    public static void main(String[] args) {
        var amounts = new TreeMap<BigDecimal, String>();
        var a = new BigDecimal("1.0");
        var b = new BigDecimal("1.00");
        amounts.put(a, "first");
        String old = amounts.put(b, "second"); // compareTo == 0: та же запись для TreeMap.
        System.out.println("equals=" + a.equals(b) + " compare=" + a.compareTo(b));
        System.out.println("size=" + amounts.size() + " old=" + old + " value=" + amounts.get(a));

        var payments = new TreeMap<Integer, String>();
        for (int id : new int[]{8, 4, 12, 2, 6, 10, 14, 5, 7}) payments.put(id, "P" + id);
        var range = payments.subMap(5, true, 11, true); // Живое представление, не копия.
        System.out.println("floor 9=" + payments.floorKey(9) + " ceiling 9=" + payments.ceilingKey(9));
        System.out.println("range=" + range.keySet());
        range.remove(6);
        System.out.println("after view removal parent has 6=" + payments.containsKey(6));
        try {
            range.put(12, "P12"); // За пределами заданного диапазона.
        } catch (IllegalArgumentException error) {
            System.out.println("put 12 via view=" + error.getClass().getSimpleName());
        }
    }
}
