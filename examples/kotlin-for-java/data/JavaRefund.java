package pay;

// Java-класс «дописывает» вариант в sealed-иерархию Kotlin.
public class JavaRefund implements PayResult {
    public static void main(String[] args) {
        System.out.println(SealedKt.httpStatus(new JavaRefund()));
    }
}
