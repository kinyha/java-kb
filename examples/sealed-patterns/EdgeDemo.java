public class EdgeDemo {
    record Box(String value) {}
    record Envelope(Box box) {}
    record Broken(int value) {
        public int value() { throw new IllegalStateException("broken accessor"); }
    }

    static String withDefault(Object value) {
        return switch (value) {
            case String s -> "string";
            default -> "other"; // does not cover null selector
        };
    }

    static String totalType(Object value) {
        return switch (value) { case Object o -> "object"; };
    }

    static String nested(Envelope envelope) {
        return switch (envelope) {
            case Envelope(Box(var text)) -> String.valueOf(text);
        }; // exhaustive for the static type, not a null-safety guarantee
    }

    static void show(String label, Runnable operation) {
        try { operation.run(); }
        catch (RuntimeException e) {
            System.out.println(label + "=" + e.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        show("default null", () -> withDefault(null));
        show("Object null", () -> totalType(null));
        System.out.println("null record=" + ((Object) null instanceof Box(var text)));
        Box box = new Box(null);
        if (box instanceof Box(var text)) System.out.println("component=" + text);
        show("null nested", () -> nested(new Envelope(null)));
        System.out.println("nested component=" + nested(new Envelope(new Box(null))));
        try {
            if (new Broken(1) instanceof Broken(var value)) System.out.println(value);
        } catch (MatchException e) {
            System.out.println("accessor=" + e.getClass().getSimpleName()
                    + ", cause=" + e.getCause().getClass().getSimpleName());
        }
    }
}
