import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.stream.Stream;

public class FilesDemo {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("java-kb-io-");
        Path file = dir.resolve("export.txt");
        try {
            Files.writeString(file, "first\nsecond\n", StandardCharsets.UTF_8);
            Files.writeString(file, "third\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
                System.out.println("lines=" + lines.count());
            }
            Files.writeString(file, "new", StandardCharsets.UTF_8); // default: truncate existing
            System.out.println("default write=" + Files.readString(file, StandardCharsets.UTF_8));
            try { Files.writeString(file, "oops", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); }
            catch (FileAlreadyExistsException expected) { System.out.println("CREATE_NEW refused overwrite"); }
            Path lexical = Path.of("exports", "..", "report.txt");
            System.out.println("normalized=" + lexical.normalize() + ", exists=" + Files.exists(lexical));
            Path pending = dir.resolve("pending.txt");
            try (FileChannel channel = FileChannel.open(pending, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = ByteBuffer.wrap("published".getBytes(StandardCharsets.UTF_8));
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true); // это вызов force, а не опыт отключения питания
            }
            try {
                Files.move(pending, dir.resolve("published.txt"), StandardCopyOption.ATOMIC_MOVE);
                System.out.println("atomic move supported");
                System.out.println("published=" + Files.readString(dir.resolve("published.txt"), StandardCharsets.UTF_8));
            } catch (AtomicMoveNotSupportedException expected) {
                System.out.println("atomic move unsupported; pending retained");
            }
        } finally {
            try (Stream<Path> files = Files.list(dir)) {
                for (Path path : files.toList()) Files.delete(path);
            }
            Files.delete(dir);
        }
    }
}
