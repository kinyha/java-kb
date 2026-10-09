import java.io.*;
import java.nio.charset.*;
import java.util.*;

public class ChunkDemo {
    // Управляемый поток: один read возвращает максимум два байта.
    static class Chunks extends ByteArrayInputStream {
        Chunks(byte[] data) { super(data); }
        @Override public synchronized int read(byte[] b, int off, int len) {
            return super.read(b, off, Math.min(len, 2));
        }
        @Override public synchronized int available() { return 0; } // не свидетельство EOF
    }
    public static void main(String[] args) throws IOException {
        byte[] source = "A€B".getBytes(StandardCharsets.UTF_8);
        System.out.println("source hex=" + HexFormat.of().formatHex(source));
        byte[] buffer = new byte[4];
        try (InputStream in = new Chunks(source)) {
            int n = in.read(buffer);
            System.out.println("one read=" + n + ", available=" + in.available());
            System.out.println("one chunk=" + new String(buffer, 0, n, StandardCharsets.UTF_8));
        }
        ByteArrayOutputStream collected = new ByteArrayOutputStream();
        StringBuilder broken = new StringBuilder();
        List<Integer> sizes = new ArrayList<>();
        try (InputStream in = new Chunks(source)) {
            int n;
            while ((n = in.read(buffer)) != -1) {
                sizes.add(n);
                collected.write(buffer, 0, n);
                broken.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
            }
        }
        System.out.println("reads=" + sizes + ", chunk decoding=" + broken);
        System.out.println("whole decoding=" + collected.toString(StandardCharsets.UTF_8));
        try (Reader reader = new InputStreamReader(new Chunks(source), StandardCharsets.UTF_8)) {
            StringBuilder text = new StringBuilder();
            char[] chars = new char[3];
            int n;
            while ((n = reader.read(chars)) != -1) text.append(chars, 0, n);
            System.out.println("reader decoding=" + text);
            if (!text.toString().equals("A€B")) throw new AssertionError("decoder lost a boundary");
        }
        CharsetDecoder strict = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT);
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(new byte[]{(byte) 0xe2}), strict)) {
            reader.read();
            throw new AssertionError("truncated UTF-8 accepted");
        } catch (MalformedInputException expected) { System.out.println("strict truncated UTF-8 rejected"); }
        String emoji = "💳";
        System.out.println("emoji chars=" + emoji.length() + ", code points=" + emoji.codePointCount(0, emoji.length()));
        if (!Arrays.equals(source, collected.toByteArray())) throw new AssertionError("byte copy changed source");
    }
}
