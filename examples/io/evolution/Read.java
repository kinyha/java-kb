import java.io.*;
import java.nio.file.*;
public class Read {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(Path.of(args[0])))) {
            Snapshot value = (Snapshot) in.readObject();
            System.out.println("reader: count=" + value.count + ", label=" + value.label);
        } catch (InvalidClassException expected) {
            System.out.println("reader: UID mismatch -> InvalidClassException");
        }
    }
}
