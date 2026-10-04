public class Main {
    public static void main(String[] args) {
        Gateway g = new Sbp();
        System.out.println("   pay:    " + g.pay(500));
        try { System.out.println("   refund: " + g.refund(500)); }
        catch (AbstractMethodError e) { System.out.println("   refund: AbstractMethodError"); }
    }
}
