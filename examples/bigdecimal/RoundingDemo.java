import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

public class RoundingDemo {
    public static void main(String[] args) {
        List<BigDecimal> lines = List.of(new BigDecimal("0.015"), new BigDecimal("0.015"), new BigDecimal("0.015"));
        BigDecimal lineFirst = lines.stream().map(x -> x.setScale(2, RoundingMode.HALF_EVEN))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal exactTotal = lines.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalFirst = exactTotal.setScale(2, RoundingMode.HALF_EVEN);
        System.out.println("line first=" + lineFirst + ", exact total=" + exactTotal + ", total first=" + totalFirst);

        BigDecimal one = BigDecimal.ONE, three = BigDecimal.valueOf(3);
        try { one.divide(three); }
        catch (ArithmeticException expected) { System.out.println("exact 1/3 rejected"); }
        System.out.println("exact 1/8=" + one.divide(BigDecimal.valueOf(8)));
        System.out.println("scaled 1/3=" + one.divide(three, 2, RoundingMode.HALF_EVEN));
        System.out.println("scale inherited=" + one.divide(three, RoundingMode.HALF_EVEN));
        BigDecimal x = new BigDecimal("123.456");
        try { x.setScale(2); }
        catch (ArithmeticException expected) { System.out.println("scale without mode rejected"); }
        System.out.println("scale 2=" + x.setScale(2, RoundingMode.HALF_EVEN));
        System.out.println("precision 4=" + x.round(new MathContext(4, RoundingMode.HALF_EVEN)));
        try { one.divide(three, MathContext.UNLIMITED); }
        catch (ArithmeticException expected) { System.out.println("unlimited 1/3 rejected"); }

        for (RoundingMode mode : RoundingMode.values()) {
            try {
                System.out.println(mode + ": " + new BigDecimal("1.225").setScale(2, mode)
                        + ", " + new BigDecimal("-1.225").setScale(2, mode));
            } catch (ArithmeticException expected) {
                System.out.println(mode + ": ArithmeticException");
            }
        }
        System.out.println("odd tie=" + new BigDecimal("1.235").setScale(2, RoundingMode.HALF_EVEN));
        BigDecimal source = new BigDecimal("1.2451");
        System.out.println("once=" + source.setScale(2, RoundingMode.HALF_EVEN)
                + ", twice=" + source.setScale(3, RoundingMode.HALF_EVEN).setScale(2, RoundingMode.HALF_EVEN));
        MathContext mc = new MathContext(3, RoundingMode.HALF_UP);
        BigDecimal a = new BigDecimal("1000"), b = BigDecimal.ONE, c = new BigDecimal("-1000");
        System.out.println("left=" + a.add(b, mc).add(c, mc).toPlainString()
                + ", right=" + a.add(b.add(c, mc), mc).toPlainString());
    }
}
