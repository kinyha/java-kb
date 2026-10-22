// JDK 28: javac --release 28 --enable-preview — ошибка компиляции, это и проверяется
public value class EarlyThis {
    private final long kopecks;
    private final String currency;

    public EarlyThis(long kopecks, String currency) {
        this.kopecks = kopecks;
        this.currency = normalize(currency);    // метод экземпляра: нужен this
    }

    private String normalize(String c) { return c.toUpperCase(); }
}
