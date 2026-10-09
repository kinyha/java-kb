public class GenericDemo {
    sealed interface Result<T> permits Text, Value {}
    record Text(String text) implements Result<String> {}
    record Value<T>(T value) implements Result<T> {}

    static int number(Result<Integer> result) {
        return switch (result) {
            case Value<Integer>(var n) -> n; // Text cannot be a Result<Integer>
        };
    }

    public static void main(String[] args) {
        System.out.println("number=" + number(new Value<>(42)));
    }
}
