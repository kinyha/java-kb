import java.io.*;

public class SerializationDemo {
    record Invoice(String id, long minorUnits) implements Serializable {
        static int constructions;
        static boolean customRead;
        Invoice {
            constructions++;
            if (id == null || id.isBlank() || minorUnits < 0) throw new IllegalArgumentException("invalid invoice");
        }
        // Java Serialization игнорирует readObject у records.
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            customRead = true;
            in.defaultReadObject();
        }
    }

    public static void main(String[] args) throws Exception {
        Invoice original = new Invoice("p1", 100);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(original);
        }
        Invoice restored;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (Invoice) in.readObject();
        }
        System.out.println("equal=" + original.equals(restored));
        System.out.println("canonical calls=" + Invoice.constructions);
        System.out.println("custom readObject=" + Invoice.customRead);
    }
}
