import java.util.function.*;

// Разбирается javap: что остаётся от лямбд и ссылок на методы в байткоде.
public class Fees {
    static LongUnaryOperator flat() { return a -> a / 100; }                  // ничего не захватывает
    static LongUnaryOperator rate(long r) { return a -> a * r / 1000; }       // захватывает r
    static Function<String, Long> parse() { return Long::parseLong; }         // ссылка на метод
}
