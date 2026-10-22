// JDK 28: java --enable-preview Construction.java
public class Construction {
    static value class Money {
        private final long kopecks;
        private final String currency;

        Money(long kopecks, String currency) {
            if (kopecks < 0) throw new IllegalArgumentException("negative");
            this.kopecks = kopecks;
            this.currency = normalize(currency);    // static-метод: this не нужен
            super();                                // к этой строке все поля обязаны быть заданы
            System.out.println("создан " + this);   // после super() this уже можно использовать
        }

        private static String normalize(String c) { return c.toUpperCase(); }

        @Override public String toString() { return kopecks + " " + currency; }
    }

    public static void main(String[] args) {
        new Money(150, "rub");
    }
}
