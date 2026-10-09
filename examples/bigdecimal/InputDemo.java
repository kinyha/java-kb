import java.math.BigDecimal;

public class InputDemo {
    public static void main(String[] args) {
        System.out.println("double sum=" + (0.1 + 0.2));
        System.out.println("text sum=" + new BigDecimal("0.1").add(new BigDecimal("0.2")));
        System.out.println("double constructor=" + new BigDecimal(0.1));
        System.out.println("valueOf literal=" + BigDecimal.valueOf(0.1));
        System.out.println("valueOf sum=" + BigDecimal.valueOf(0.1 + 0.2));
        System.out.println("minor units=" + BigDecimal.valueOf(1234, 2));
        try { BigDecimal.valueOf(Double.NaN); }
        catch (NumberFormatException expected) { System.out.println("NaN rejected"); }
    }
}
