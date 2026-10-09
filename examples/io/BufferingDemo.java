import java.io.*;

public class BufferingDemo {
    static class Sink extends OutputStream {
        int calls, bytes;
        boolean closed;
        @Override public void write(int b) { calls++; bytes++; }
        @Override public void write(byte[] b, int off, int len) { calls++; bytes += len; }
        @Override public void close() { closed = true; }
    }
    public static void main(String[] args) throws IOException {
        Sink sink = new Sink();
        try (OutputStream out = new BufferedOutputStream(sink, 4)) {
            out.write(1); out.write(2); out.write(3);
            System.out.println("before flush: calls=" + sink.calls + ", bytes=" + sink.bytes);
            out.flush();
            System.out.println("after flush: calls=" + sink.calls + ", bytes=" + sink.bytes);
            out.write(4);
        }
        System.out.println("after close: calls=" + sink.calls + ", bytes=" + sink.bytes + ", closed=" + sink.closed);
        PrintWriter print = new PrintWriter(new Writer() {
            public void write(char[] b, int off, int len) throws IOException { throw new IOException("sink failed"); }
            public void flush() {}
            public void close() {}
        });
        print.println("export");
        System.out.println("PrintWriter error=" + print.checkError());
        print.close();
    }
}
