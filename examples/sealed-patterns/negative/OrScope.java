class OrScope {
    static boolean nonEmpty(Object value) {
        return value instanceof String s || !s.isEmpty(); // false path has no s
    }
}
