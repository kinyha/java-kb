// JDK 28: javac --release 28 --enable-preview — три ошибки компиляции, это и проверяется
public class Rules {
    static abstract class Account { }                          // identity-класс
    static value class Card extends Account { }                // value не может наследовать identity

    static abstract value class Amount { abstract long kopecks(); }
    static value class Fee extends Amount {                    // можно: родитель — abstract value
        private final long k;
        Fee(long k) { this.k = k; }
        long kopecks() { return k; }
    }
    static class Balance extends Amount {                      // можно: identity-наследник abstract value
        long k;
        long kopecks() { return k; }
    }
    static value class Bonus extends Fee { Bonus() { super(1); } }   // value class неявно final

    static value class Counter {
        long n;                                                // неявно final
        Counter(long n) { this.n = n; }
        void inc() { n++; }                                    // запись после конструктора
    }
}
