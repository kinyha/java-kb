import java.io.File;
import java.io.FileInputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

// Запуск: java TlsDemo.java   (JDK 21; части 1–2 ходят в интернет, часть 3 — loopback)
public class TlsDemo {

    public static void main(String[] args) throws Exception {
        handshakes();
        failures();
        mutualTls();
    }

    // 1. Рукопожатие TLS 1.2 и 1.3 с одним сервером.
    static void handshakes() throws Exception {
        System.out.println("== handshake");
        var factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        for (String protocol : List.of("TLSv1.2", "TLSv1.3")) {
            try (var tcp = new java.net.Socket("example.com", 443)) {      // TCP уже установлен.
                var s = (SSLSocket) factory.createSocket(tcp, "example.com", 443, true);
                s.setEnabledProtocols(new String[] {protocol});
                long t = System.nanoTime();
                s.startHandshake();                                          // Только TLS.
                long ms = (System.nanoTime() - t) / 1_000_000;
                var session = s.getSession();
                System.out.println(session.getProtocol() + " " + session.getCipherSuite() + " in " + ms + " ms");
                if (protocol.equals("TLSv1.3")) {
                    for (var c : session.getPeerCertificates()) {
                        var x = (X509Certificate) c;
                        System.out.println("  cert: " + x.getSubjectX500Principal().getName().replaceAll(",.*", "")
                                + " <- " + x.getIssuerX500Principal().getName().replaceAll(".*CN=([^,]+).*", "CN=$1"));
                    }
                    var leaf = (X509Certificate) session.getPeerCertificates()[0];
                    System.out.println("  SAN: " + leaf.getSubjectAlternativeNames().stream().map(l -> l.get(1)).toList()
                            + ", valid until " + leaf.getNotAfter().toInstant().toString().substring(0, 10));
                }
            }
        }
    }

    // 2. Что делает клиент, когда сертификат не проходит проверку.
    static void failures() throws Exception {
        System.out.println("== certificate checks");
        var factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        for (String host : List.of("expired.badssl.com", "wrong.host.badssl.com", "self-signed.badssl.com")) {
            try (var s = (SSLSocket) factory.createSocket(host, 443)) {
                var params = s.getSSLParameters();
                params.setEndpointIdentificationAlgorithm("HTTPS"); // Проверять имя, как HttpClient.
                s.setSSLParameters(params);
                s.startHandshake();
                System.out.println(host + ": OK");
            } catch (Exception e) {
                String msg = e.getMessage();
                System.out.println(host + ": " + e.getClass().getSimpleName() + ": "
                        + msg.substring(msg.lastIndexOf(':') + 1).trim());
            }
        }
    }

    // 3. mTLS на loopback: сервер требует сертификат клиента.
    static void mutualTls() throws Exception {
        System.out.println("== mutual TLS");
        File dir = Files.createTempDirectory("mtls").toFile();
        keytool(dir, "server"); keytool(dir, "client");
        // Каждая сторона доверяет сертификату другой.
        exportImport(dir, "server", "client-trust.p12");
        exportImport(dir, "client", "server-trust.p12");

        SSLContext serverCtx = context(dir, "server.p12", "server-trust.p12");
        try (var server = (SSLServerSocket) serverCtx.getServerSocketFactory().createServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            server.setNeedClientAuth(true); // Без сертификата клиента — отказ.
            Thread.ofPlatform().start(() -> {
                for (int i = 0; i < 2; i++) {
                    try (var s = (SSLSocket) server.accept()) {
                        s.startHandshake();
                        String who = s.getSession().getPeerPrincipal().getName();
                        s.getOutputStream().write(("hello " + who + "\n").getBytes());
                    } catch (Exception ignored) { }
                }
            });
            for (boolean withCert : new boolean[] {false, true}) {
                SSLContext clientCtx = context(dir, withCert ? "client.p12" : null, "client-trust.p12");
                try (var s = (SSLSocket) clientCtx.getSocketFactory().createSocket(InetAddress.getLoopbackAddress(), server.getLocalPort())) {
                    s.startHandshake();
                    String reply = new String(s.getInputStream().readNBytes(64)).trim();
                    System.out.println("client cert=" + withCert + ": " + (reply.isEmpty() ? "connection closed by server" : reply));
                } catch (Exception e) {
                    System.out.println("client cert=" + withCert + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        }
    }

    static void keytool(File dir, String name) throws Exception {
        run(dir, "keytool", "-genkeypair", "-alias", name, "-keyalg", "EC", "-groupname", "secp256r1",
                "-dname", "CN=" + name + ".payments.test", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-validity", "2", "-keystore", name + ".p12", "-storepass", "changeit", "-storetype", "PKCS12");
    }

    static void exportImport(File dir, String name, String trustStore) throws Exception {
        run(dir, "keytool", "-exportcert", "-alias", name, "-keystore", name + ".p12", "-storepass", "changeit", "-file", name + ".crt");
        run(dir, "keytool", "-importcert", "-noprompt", "-alias", name, "-file", name + ".crt",
                "-keystore", trustStore, "-storepass", "changeit", "-storetype", "PKCS12");
    }

    static void run(File dir, String... cmd) throws Exception {
        var p = new ProcessBuilder(cmd).directory(dir).redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        if (p.waitFor() != 0) throw new IllegalStateException(String.join(" ", cmd));
    }

    static SSLContext context(File dir, String keyStore, String trustStore) throws Exception {
        char[] pass = "changeit".toCharArray();
        KeyManagerFactory kmf = null;
        if (keyStore != null) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (var in = new FileInputStream(new File(dir, keyStore))) { ks.load(in, pass); }
            kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, pass);
        }
        KeyStore ts = KeyStore.getInstance("PKCS12");
        try (var in = new FileInputStream(new File(dir, trustStore))) { ts.load(in, pass); }
        var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf == null ? null : kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return ctx;
    }
}
