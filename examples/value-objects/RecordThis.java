// JDK 21 или JDK 28 без preview: запускается. JDK 28 с --enable-preview: не компилируется.
public class RecordThis {
    record Payment(String id, long amount) {
        Payment {
            if (amount < 0)
                throw new IllegalArgumentException("bad " + this);   // this до присваивания полей
        }
    }

    public static void main(String[] args) {
        try {
            new Payment("p-1", -500);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }
    }
}
