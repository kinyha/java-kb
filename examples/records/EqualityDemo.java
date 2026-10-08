import java.math.BigDecimal;
import java.util.*;

public class EqualityDemo {
    record ArrayKey(int[] values) {}
    record ListKey(List<Integer> values) {
        ListKey { values = List.copyOf(values); }
    }
    record Reading(double value) {}
    record Amount(BigDecimal value) {}
    record Misleading(int value) {
        public int value() { return Math.abs(value); } // нарушает copy invariant для отрицательного value
    }

    public static void main(String[] args) {
        System.out.println("arrays=" + new ArrayKey(new int[]{1, 2}).equals(new ArrayKey(new int[]{1, 2})));
        System.out.println("lists=" + new ListKey(List.of(1, 2)).equals(new ListKey(List.of(1, 2))));
        System.out.println("NaN=" + new Reading(Double.NaN).equals(new Reading(Double.NaN)));
        System.out.println("signed zero=" + new Reading(0.0).equals(new Reading(-0.0)));
        System.out.println("decimal scale=" + new Amount(new BigDecimal("1.0")).equals(new Amount(new BigDecimal("1.00"))));
        Misleading source = new Misleading(-10);
        Misleading copy = new Misleading(source.value());
        System.out.println("accessor=" + source.value() + ", source=" + source + ", copy=" + copy);
        System.out.println("copy invariant=" + source.equals(copy));
    }
}
