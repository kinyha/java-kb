import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public class BufferDemo {
    static class PartialSink implements WritableByteChannel {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int calls;
        boolean open = true;
        public int write(ByteBuffer src) {
            calls++;
            int n = Math.min(2, src.remaining());
            for (int i = 0; i < n; i++) bytes.write(src.get());
            return n;
        }
        public boolean isOpen() { return open; }
        public void close() { open = false; }
    }
    static void state(String name, ByteBuffer b) {
        System.out.println(name + ": pos=" + b.position() + ", limit=" + b.limit()
                + ", capacity=" + b.capacity() + ", memory=" + HexFormat.of().formatHex(b.array()));
    }
    public static void main(String[] args) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(6);
        b.put("ABCD".getBytes(StandardCharsets.US_ASCII)); state("put ABCD", b);
        b.flip(); state("flip", b);
        System.out.println("consumed=" + (char) b.get() + (char) b.get()); state("get AB", b);
        b.compact(); state("compact", b);
        b.put("EF".getBytes(StandardCharsets.US_ASCII)); b.flip(); state("put EF, flip", b);
        try (PartialSink sink = new PartialSink()) {
            while (b.hasRemaining()) sink.write(b);
            System.out.println("channel output=" + sink.bytes.toString(StandardCharsets.US_ASCII)
                    + ", writes=" + sink.calls + ", pos=" + b.position());
        }
        b.clear(); state("clear", b);
        System.out.println("clear first byte=" + (char) b.get());
        ByteBuffer noFlip = ByteBuffer.allocate(4);
        noFlip.put("ABC".getBytes(StandardCharsets.US_ASCII));
        try (PartialSink sink = new PartialSink()) {
            while (noFlip.hasRemaining()) sink.write(noFlip);
            System.out.println("without flip hex=" + HexFormat.of().formatHex(sink.bytes.toByteArray()));
        }
    }
}
