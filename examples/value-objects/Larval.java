// JDK 28: javac --release 28 --enable-preview, затем javap -v -p Larval
value class Larval {
    private final long kopecks;
    private final String currency;
    Larval(long kopecks, String currency) {
        this.kopecks = kopecks;
        this.currency = currency == null ? "RUB" : currency;
    }
}
