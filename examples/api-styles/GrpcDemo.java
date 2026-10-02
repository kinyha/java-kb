import io.grpc.CallOptions;
import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

// Запуск: ./run.sh GrpcDemo   (JDK 21, gRPC 1.84 поверх Netty, loopback)
// Без кодогенерации: сообщения — строки, чтобы показать сам транспорт gRPC.
public class GrpcDemo {

    // Маршаллер превращает сообщение в байты кадра gRPC; обычно это Protobuf.
    static final MethodDescriptor.Marshaller<String> TEXT = new MethodDescriptor.Marshaller<>() {
        public InputStream stream(String s) { return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)); }
        public String parse(InputStream in) {
            try { return new String(in.readAllBytes(), StandardCharsets.UTF_8); } catch (Exception e) { throw new RuntimeException(e); }
        }
    };

    static final MethodDescriptor<String, String> GET = MethodDescriptor.<String, String>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("payments.Payments/GetPayment") // HTTP/2 путь: /payments.Payments/GetPayment
            .setRequestMarshaller(TEXT).setResponseMarshaller(TEXT).build();

    static final MethodDescriptor<String, String> WATCH = MethodDescriptor.<String, String>newBuilder()
            .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
            .setFullMethodName("payments.Payments/WatchPayment")
            .setRequestMarshaller(TEXT).setResponseMarshaller(TEXT).build();

    public static void main(String[] args) throws Exception {
        ServerServiceDefinition service = ServerServiceDefinition.builder("payments.Payments")
                .addMethod(GET, ServerCalls.asyncUnaryCall((id, out) -> {
                    var deadline = Context.current().getDeadline(); // Срок клиента виден серверу.
                    if (id.equals("slow")) {
                        System.out.println("server: deadline left ~" + (deadline == null ? "none"
                                : (deadline.timeRemaining(TimeUnit.MILLISECONDS) / 10 * 10) + " ms"));
                        sleep(300);                                // Банк отвечает медленно.
                        System.out.println("server: cancelled by client=" + Context.current().isCancelled());
                        return;
                    }
                    if (!id.startsWith("p-")) {
                        out.onError(Status.NOT_FOUND.withDescription("payment " + id + " not found").asRuntimeException());
                        return;
                    }
                    out.onNext(id + ":CAPTURED");
                    out.onCompleted();
                }))
                .addMethod(WATCH, ServerCalls.asyncServerStreamingCall((id, out) -> {
                    for (String s : new String[] {"AUTHORIZED", "CAPTURED", "SETTLED"}) {
                        out.onNext(id + ":" + s);                   // Каждое — отдельное сообщение потока.
                        sleep(50);
                    }
                    out.onCompleted();
                }))
                .build();

        Server server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0)).addService(service).build().start();
        ManagedChannel channel = NettyChannelBuilder.forAddress("127.0.0.1", server.getPort()).usePlaintext().build();

        System.out.println("unary: " + ClientCalls.blockingUnaryCall(channel, GET, CallOptions.DEFAULT, "p-1"));
        try {
            ClientCalls.blockingUnaryCall(channel, GET, CallOptions.DEFAULT, "x-9");
        } catch (StatusRuntimeException e) {
            System.out.println("unknown: " + e.getStatus().getCode() + " — " + e.getStatus().getDescription());
        }
        long t = System.nanoTime();
        try {
            ClientCalls.blockingUnaryCall(channel, GET, CallOptions.DEFAULT.withDeadlineAfter(100, TimeUnit.MILLISECONDS), "slow");
        } catch (StatusRuntimeException e) {
            System.out.println("deadline: " + e.getStatus().getCode() + " after ~" + ((System.nanoTime() - t) / 1_000_000 / 10 * 10) + " ms");
        }
        sleep(350);
        var it = ClientCalls.blockingServerStreamingCall(channel, WATCH, CallOptions.DEFAULT, "p-1");
        StringBuilder events = new StringBuilder();
        it.forEachRemaining(e -> events.append(e).append(' '));
        System.out.println("stream: " + events.toString().trim());

        channel.shutdownNow();
        server.shutdownNow();
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
