class Unchecked {
    record Box<T>(T value) {}
    static boolean stringBox(Object value) {
        return value instanceof Box<String>(var s); // erased type cannot prove String
    }
}
