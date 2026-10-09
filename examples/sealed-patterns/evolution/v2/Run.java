public class Run {
    public static void main(String[] args) {
        try { System.out.println(Client.route(new Retry())); }
        catch (MatchException e) { System.out.println("old client=" + e.getClass().getSimpleName()); }
    }
}
