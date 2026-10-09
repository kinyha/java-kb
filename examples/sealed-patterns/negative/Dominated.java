class Dominated {
    static int length(Object value) {
        return switch (value) {
            case CharSequence cs -> cs.length();
            case String s -> s.length(); // every String already matched CharSequence
            default -> 0;
        };
    }
}
