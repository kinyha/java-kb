import java.io.*;

public class GraphDemo {
    static class Base {
        static int calls;
        String origin = "base-default";
        Base() { calls++; }
    }
    static class Node extends Base implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        static int constructors;
        static String environment = "before";
        String name;
        transient String cache = "initializer";
        Node next;
        Node(String name) { constructors++; this.name = name; origin = "changed"; cache = "calculated"; }
    }
    static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) { out.writeObject(value); }
        return bytes.toByteArray();
    }
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        Node a = new Node("A"), b = new Node("B"); a.next = b; b.next = a;
        byte[] bytes = serialize(new Node[]{a, a, b});
        Base.calls = 0; Node.constructors = 0; Node.environment = "after";
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            Node[] copy = (Node[]) in.readObject();
            System.out.println("alias=" + (copy[0] == copy[1]) + ", cycle=" + (copy[0].next.next == copy[0])
                    + ", original=" + (copy[0] == a));
            System.out.println("node constructors=" + Node.constructors + ", base constructors=" + Base.calls);
            System.out.println("transient=" + copy[0].cache + ", base field=" + copy[0].origin
                    + ", static=" + Node.environment);
        }
        try { serialize(new Object()); throw new AssertionError("nonserializable accepted"); }
        catch (NotSerializableException expected) { System.out.println("nonserializable rejected"); }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            in.setObjectInputFilter(info -> info.depth() > 2
                    ? ObjectInputFilter.Status.REJECTED : ObjectInputFilter.Status.UNDECIDED);
            try { in.readObject(); throw new AssertionError("deep graph accepted"); }
            catch (InvalidClassException expected) { System.out.println("depth filter rejected"); }
        }
    }
}
