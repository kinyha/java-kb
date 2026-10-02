import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

// Запуск: java TcpDemo.java   (JDK 21, loopback)
public class TcpDemo {
    static final InetAddress LO = InetAddress.getLoopbackAddress();

    public static void main(String[] args) throws Exception {
        boundaries();
        refusedVsTimeout();
        flowControl();
        timeWait();
    }

    // TCP — поток байтов: три записи могут прийти одним чтением. UDP хранит границы.
    static void boundaries() throws Exception {
        System.out.println("== message boundaries");
        try (var server = new ServerSocket(0, 50, LO);
             var client = new Socket(LO, server.getLocalPort());
             var peer = server.accept()) {
            OutputStream out = client.getOutputStream();
            for (String part : List.of("pay:17;", "pay:18;", "pay:19;")) out.write(part.getBytes(StandardCharsets.US_ASCII));
            Thread.sleep(50); // Даём байтам дойти.
            byte[] buf = new byte[1024];
            int n = peer.getInputStream().read(buf);
            System.out.println("tcp: 3 writes, first read got " + n + " bytes: " + new String(buf, 0, n, StandardCharsets.US_ASCII));
        }
        try (var rx = new DatagramSocket(0, LO); var tx = new DatagramSocket()) {
            for (String part : List.of("pay:17;", "pay:18;", "pay:19;")) {
                byte[] b = part.getBytes(StandardCharsets.US_ASCII);
                tx.send(new DatagramPacket(b, b.length, LO, rx.getLocalPort()));
            }
            List<String> got = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                var p = new DatagramPacket(new byte[1024], 1024);
                rx.receive(p);
                got.add(new String(p.getData(), 0, p.getLength(), StandardCharsets.US_ASCII));
            }
            System.out.println("udp: 3 sends, 3 receives: " + got);
        }
    }

    // Закрытый порт отвечает RST сразу; молчащий адрес — ждём таймаута.
    static void refusedVsTimeout() throws Exception {
        System.out.println("== refused vs timeout");
        int closedPort;
        try (var tmp = new ServerSocket(0, 50, LO)) { closedPort = tmp.getLocalPort(); } // Порт теперь закрыт.
        long t = System.nanoTime();
        try (var s = new Socket()) {
            s.connect(new InetSocketAddress(LO, closedPort), 2000);
        } catch (IOException e) {
            System.out.println("closed port: " + e.getClass().getSimpleName() + " '" + e.getMessage() + "' after "
                    + (System.nanoTime() - t) / 1_000_000 + " ms");
        }
        // Сокет слушает, но backlog переполнен и accept не вызывается.
        try (var server = new ServerSocket(0, 1, LO)) {
            List<Socket> filler = new ArrayList<>();
            String result = "all connected";
            for (int i = 0; i < 10; i++) {
                var s = new Socket();
                t = System.nanoTime();
                try {
                    s.connect(new InetSocketAddress(LO, server.getLocalPort()), 500);
                    filler.add(s);
                } catch (IOException e) { // Linux обычно молчит (таймаут), macOS отвечает RST.
                    result = "connect #" + (i + 1) + ": " + e.getClass().getSimpleName() + " '" + e.getMessage()
                            + "' after " + (System.nanoTime() - t) / 1_000_000 + " ms";
                    s.close();
                    break;
                }
            }
            System.out.println("full backlog (accept never called): " + result);
            for (var s : filler) s.close();
        }
    }

    // Получатель не читает: запись блокируется, когда заполнены буферы обеих сторон.
    static void flowControl() throws Exception {
        System.out.println("== flow control");
        try (var server = new ServerSocket(0, 50, LO);
             var client = new Socket(LO, server.getLocalPort());
             var peer = server.accept()) {
            System.out.println("buffers: send=" + client.getSendBufferSize() + ", receive=" + peer.getReceiveBufferSize());
            AtomicLong written = new AtomicLong();
            Thread writer = Thread.ofPlatform().start(() -> {
                try {
                    OutputStream out = client.getOutputStream();
                    byte[] chunk = new byte[8192];
                    while (true) { out.write(chunk); written.addAndGet(chunk.length); }
                } catch (IOException ignored) { }
            });
            Thread.sleep(500);
            long stalled = written.get();
            Thread.sleep(500);
            System.out.println("writer blocked after " + stalled / 1024 + " KB, still " + written.get() / 1024
                    + " KB a moment later, state=" + writer.getState());
            InputStream in = peer.getInputStream();
            byte[] buf = new byte[65536];
            long read = 0;
            while (read < 256 * 1024) read += in.read(buf);       // Читатель освободил окно.
            Thread.sleep(200);
            System.out.println("after reading 256 KB the writer moved on: " + written.get() / 1024 + " KB");
            client.close();
        }
    }

    // Кто закрывает первым, у того остаётся TIME_WAIT: та же четвёрка недоступна.
    static void timeWait() throws Exception {
        System.out.println("== TIME_WAIT");
        try (var server = new ServerSocket(0, 50, LO)) {
            Thread acceptor = Thread.ofPlatform().start(() -> {
                try { while (true) { Socket s = server.accept(); s.getInputStream().read(); s.close(); } }
                catch (IOException ignored) { }
            });
            int clientPort;
            try (var c = new Socket()) {
                c.bind(new InetSocketAddress(LO, 0));
                c.connect(new InetSocketAddress(LO, server.getLocalPort()));
                clientPort = c.getLocalPort();
            } // Клиент закрыл первым.
            for (boolean reuse : new boolean[] {false, true}) {
                String stage = "bind";
                try (var again = new Socket()) {
                    again.setReuseAddress(reuse);
                    again.bind(new InetSocketAddress(LO, clientPort));   // Тот же локальный порт...
                    stage = "connect";
                    again.connect(new InetSocketAddress(LO, server.getLocalPort())); // ...и тот же сервер.
                    System.out.println("reuseAddress=" + reuse + ": reconnected from the same port");
                } catch (IOException e) {
                    System.out.println("reuseAddress=" + reuse + ": " + stage + " failed: " + e.getMessage());
                }
            }
            server.close();
            acceptor.join(1000);
        }
    }
}
