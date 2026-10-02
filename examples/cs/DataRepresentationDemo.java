import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;

// JDK 21: java examples/cs/DataRepresentationDemo.java
public class DataRepresentationDemo {
    public static void main(String[] args) throws CharacterCodingException {
        symptoms();
        twosComplement();
        promotion();
        floatingPoint();
        textUnits();
        decoding();
    }

    static void symptoms() {
        System.out.println("[symptoms]");
        int firstBatch = 1_500_000_000;               // Копейки, оба пакета по отдельности влезают в int.
        int secondBatch = 1_000_000_000;
        int dailyTotal = firstBatch + secondBatch;  // Сумма уже не влезает в 32 знаковых бита.
        System.out.println("daily total = " + dailyTotal);
        System.out.println("0.1 + 0.2 == 0.3: " + (0.1 + 0.2 == 0.3));
        String receipt = "A\u042F\u20AC\uD83D\uDCB3"; // AЯ€💳: последний знак занимает два char.
        System.out.println("receipt chars/bytes = " + receipt.length() + "/"
                + receipt.getBytes(StandardCharsets.UTF_8).length);
    }

    static void twosComplement() {
        System.out.println("[twos-complement]");
        byte delta = -5;
        String bits = String.format(Locale.ROOT, "%8s",
                Integer.toBinaryString(delta & 0xff)).replace(' ', '0'); // Только младшие 8 бит.
        System.out.println("-5 bits = " + bits);
        System.out.println("unsigned byte = " + Byte.toUnsignedInt(delta)); // Та же восьмёрка бит.
        System.out.printf(Locale.ROOT, "widen to int = %08X%n", (int) delta); // Знак расширяется.
        byte min = Byte.MIN_VALUE;
        System.out.println("-min as int/byte = " + (-min) + "/" + (byte) -min);
    }

    static void promotion() {
        System.out.println("[promotion]");
        int rubles = 30_000_000;
        long late = rubles * 100;          // Сначала int * int, затем расширение повреждённого результата.
        long early = (long) rubles * 100;  // long участвует в умножении: вычисление сразу 64-битное.
        System.out.println("late/early = " + late + "/" + early);
        try {
            System.out.println(Math.multiplyExact(rubles, 100)); // int-версия сообщает о переполнении.
        } catch (ArithmeticException e) {
            System.out.println("multiplyExact = " + e.getClass().getSimpleName());
        }
        byte count = 127;
        System.out.println("count + 1 = " + (count + 1)); // byte расширяется до int перед сложением.
        count++;                                       // Сложение сужается обратно до byte.
        System.out.println("count++ = " + count);
    }

    static void floatingPoint() {
        System.out.println("[floating-point]");
        double sum = 0.1 + 0.2; // Оба литерала и результат округляются до доступного binary64.
        System.out.println("sum = " + sum);
        System.out.println("0.1 hex = " + Double.toHexString(0.1));
        System.out.printf(Locale.ROOT, "0.1 raw = %016X%n", Double.doubleToRawLongBits(0.1));
        double big = 9_007_199_254_740_992d; // 2^53: расстояние до следующего double равно двум.
        System.out.println("2^53 + 1 == 2^53: " + (big + 1 == big));
        System.out.println("ulp(2^53) = " + Math.ulp(big));
        System.out.println("order = " + ((1e16 + -1e16) + 1) + "/" + (1e16 + (-1e16 + 1)));
        double invalid = 0.0 / 0.0; // NaN проходит проверку только на отрицательность.
        System.out.println("NaN < 0 / NaN == NaN = " + (invalid < 0) + "/" + (invalid == invalid));
        System.out.println("1/+0 / 1/-0 = " + (1.0 / 0.0) + "/" + (1.0 / -0.0));
    }

    static void textUnits() {
        System.out.println("[text-units]");
        String receipt = "A\u042F\u20AC\uD83D\uDCB3"; // AЯ€💳.
        System.out.println("UTF-16 units = " + receipt.length());
        System.out.println("code points = " + receipt.codePointCount(0, receipt.length()));
        System.out.println("UTF-8 bytes = " + receipt.getBytes(StandardCharsets.UTF_8).length);
        System.out.printf(Locale.ROOT, "charAt(3) / codePointAt(3) = %04X/%X%n",
                (int) receipt.charAt(3), receipt.codePointAt(3));
        System.out.println("bytes = " + HexFormat.of().withUpperCase().withDelimiter(" ")
                .formatHex(receipt.getBytes(StandardCharsets.UTF_8)));
        String composed = "\u00E9";       // é: одна code point.
        String decomposed = "e\u0301";   // Та же буква на экране: e и combining acute accent.
        System.out.println("e-acute units = " + composed.length() + "/" + decomposed.length());
        System.out.println("equals / NFC equals = " + composed.equals(decomposed) + "/"
                + composed.equals(Normalizer.normalize(decomposed, Normalizer.Form.NFC)));
    }

    static void decoding() throws CharacterCodingException {
        System.out.println("[decoding]");
        byte[] euro = "\u20AC".getBytes(StandardCharsets.UTF_8); // E2 82 AC — один знак в трёх байтах.
        byte[] cut = Arrays.copyOf(euro, 2);                     // E2 82: последовательность оборвана.
        String replaced = new String(cut, StandardCharsets.UTF_8); // Конструктор заменяет ошибку.
        System.out.printf(Locale.ROOT, "replacement = U+%04X%n", replaced.codePointAt(0));
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)  // На границе платежного API нужен отказ.
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(cut));
        } catch (CharacterCodingException e) {
            System.out.println("strict = " + e.getClass().getSimpleName());
        }
        String wrong = new String(euro, StandardCharsets.ISO_8859_1); // Все байты допустимы, смысл другой.
        System.out.println("wrong charset = " + wrong.codePoints()
                .mapToObj(cp -> String.format(Locale.ROOT, "U+%04X", cp)).toList());
        String recovered = new String(wrong.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        System.out.println("recover known mistake = " + recovered.equals("\u20AC"));
    }
}
