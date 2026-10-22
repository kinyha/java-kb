// JDK 28: java --enable-preview Predict.java
public class Predict {
    value record Fee(Integer kopecks, String currency) { }

    public static void main(String[] args) {
        Fee f1 = new Fee(1996, "RUB");
        Fee f2 = new Fee(1996, "RUB");
        Fee f3 = new Fee(1996, "rub".toUpperCase());
        System.out.println(f1 == f2);
        System.out.println(f1 == f3);
        System.out.println(f1.equals(f3));
    }
}
