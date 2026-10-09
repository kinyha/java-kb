import java.io.*;
import java.nio.file.*;
public class Write {
    public static void main(String[] args) throws IOException {
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(Path.of(args[0])))) {
            out.writeObject(new Snapshot());
        }
        System.out.println("writer: UID=1, count=7");
    }
}
