// Разбирается javap: что компилятор добавляет во внутренние, вложенные и анонимные классы.
public class Outer {
    private long total;                                  // приватное поле внешнего класса

    class Inner {                                        // внутренний: держит ссылку на Outer
        void add(long a) { total += a; }
    }
    class Idle {                                         // внутренний, но Outer не использует
        long twice(long a) { return a * 2; }
    }
    static class Nested {                                // статический вложенный: ссылки нет
        long twice(long a) { return a * 2; }
    }
    Runnable anonymous() {
        return new Runnable() { public void run() { total++; } };
    }
    Runnable lambda() {
        return () -> total++;
    }
}
