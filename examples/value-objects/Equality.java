// JDK 28: java --enable-preview Equality.java
public class Equality {
    value record Money(long kopecks, String currency) { }

    static value class Iban {                              // value class без equals и hashCode
        private final String digits;
        Iban(String digits) { this.digits = digits; }
    }

    value record Rate(double percent) { }

    public static void main(String[] args) {
        String rub = "RUB";
        String rubCopy = new String("RUB");                // та же строка, другой объект
        Money a = new Money(100, rub);
        Money b = new Money(100, rub);
        Money c = new Money(100, rubCopy);
        System.out.println("a == b        " + (a == b));
        System.out.println("a == c        " + (a == c));
        System.out.println("a.equals(c)   " + a.equals(c));

        Iban i1 = new Iban("DE89370400440532013000");
        Iban i2 = new Iban(new String("DE89370400440532013000"));
        System.out.println("iban equals   " + i1.equals(i2));            // унаследованный Object.equals
        System.out.println("iban hash ==  " + (i1.hashCode() == i2.hashCode()));

        System.out.println("0.0 == -0.0   " + (0.0 == -0.0));
        System.out.println("Rate(0.0) == Rate(-0.0)  " + (new Rate(0.0) == new Rate(-0.0)));
        double nan = Double.NaN;
        System.out.println("nan == nan    " + (nan == nan));
        System.out.println("Rate(nan) == Rate(nan)   " + (new Rate(nan) == new Rate(nan)));
    }
}
