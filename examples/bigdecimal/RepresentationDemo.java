import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

public class RepresentationDemo {
    static void describe(String label, BigDecimal value) {
        System.out.println(label + ": value=" + value + ", unscaled=" + value.unscaledValue()
                + ", scale=" + value.scale() + ", precision=" + value.precision());
    }

    public static void main(String[] args) {
        BigDecimal price = new BigDecimal("12.340");
        describe("price", price);
        describe("tiny", new BigDecimal("0.00120"));
        describe("stripped", new BigDecimal("1200").stripTrailingZeros());
        System.out.println("plain=" + new BigDecimal("1200").stripTrailingZeros().toPlainString());
        price.setScale(2); // return value is discarded; price stays unchanged
        System.out.println("unchanged=" + price);
        System.out.println("sum=" + price.add(new BigDecimal("0.66")));
        System.out.println("product=" + price.multiply(new BigDecimal("2.0")));

        BigDecimal a = new BigDecimal("7.0"), b = new BigDecimal("7.00");
        System.out.println("equals=" + a.equals(b) + ", compareTo=" + a.compareTo(b));
        System.out.println("hash set=" + new HashSet<>(List.of(a, b)).size());
        System.out.println("tree set=" + new TreeSet<>(List.of(a, b)).size());
        // This overload uses the dividend's scale, so these are not interchangeable.
        System.out.println("scale 1 division=" + a.divide(BigDecimal.valueOf(4), RoundingMode.HALF_EVEN));
        System.out.println("scale 2 division=" + b.divide(BigDecimal.valueOf(4), RoundingMode.HALF_EVEN));
    }
}
