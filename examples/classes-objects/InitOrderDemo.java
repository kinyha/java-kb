// Запуск: java InitOrderDemo.java   (JDK 21, без зависимостей)
// Порядок инициализации класса и объекта; каждый шаг печатает себя.
public class InitOrderDemo {
    static String log(String s) { System.out.println("   " + s); return s; }

    static class Base {
        static String S1 = log("Base static field");
        static { log("Base static block"); }
        String f1 = log("Base instance field");
        { log("Base instance block"); }
        Base() {
            log("Base constructor");
            describe();                                   // виртуальный вызов из конструктора
        }
        void describe() { log("Base.describe"); }
    }

    static class Card extends Base {
        static String S2 = log("Card static field");
        static { log("Card static block"); }
        private final String mask = log("Card instance field");
        { log("Card instance block"); }
        Card() {
            super();                                      // неявно было бы то же самое
            log("Card constructor, mask = " + mask);
        }
        @Override void describe() { log("Card.describe sees mask = " + mask); }
    }

    static class Limits {
        static final int MAX = 100_000;                   // константа времени компиляции: подставляется в код
        static final Integer BOXED = 5;                   // не константа: требует инициализации класса
        static { log("Limits initialized"); }
    }

    public static void main(String[] args) {
        System.out.println("== first new Card()");
        new Card();
        System.out.println("== second new Card(): static part is not repeated");
        new Card();
        System.out.println("== class initialization is lazy");
        System.out.println("   Limits.MAX = " + Limits.MAX + " (no init: constant inlined)");
        System.out.println("   Limits.BOXED = " + Limits.BOXED);
        System.out.println("== Class.forName vs .class");
        Class<?> c = Lazy.class;
        System.out.println("   got " + c.getSimpleName() + ".class, not initialized yet");
        try { Class.forName(c.getName()); } catch (ClassNotFoundException e) { throw new RuntimeException(e); }
    }

    static class Lazy { static { log("Lazy initialized by Class.forName"); } }
}
