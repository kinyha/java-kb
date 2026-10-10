import java.util.*;

public class RetrievalDemo {
    record Doc(String id, String tenant, String text) {}
    record Key(String tenant, String principal, int aclVersion, String query) {}
    static List<String> retrieve(List<Doc> docs, String tenant, Set<String> allowed) {
        // Enforce ACL before the text enters the model context; similarity never grants access.
        return docs.stream().filter(d -> d.tenant().equals(tenant) && allowed.contains(d.id()))
                .map(Doc::text).toList();
    }
    static void check(Object got, Object wanted) {
        if (!got.equals(wanted)) throw new AssertionError(got + " != " + wanted);
    }
    public static void main(String[] args) {
        var docs = List.of(new Doc("D1", "A", "A invoice paid"), new Doc("D2", "B", "B private note"));
        var badCache = new HashMap<String, List<String>>();
        badCache.put("invoice", retrieve(docs, "B", Set.of("D2")));
        var leaked = badCache.computeIfAbsent("invoice", q -> retrieve(docs, "A", Set.of("D1")));
        check(leaked, List.of("B private note")); // loader/ACL never ran: another tenant populated cache
        System.out.println("query-only cache for A=" + leaked);
        var cache = new HashMap<Key, List<String>>();
        cache.put(new Key("B", "bob", 1, "invoice"), retrieve(docs, "B", Set.of("D2")));
        var visible = cache.computeIfAbsent(new Key("A", "alice", 1, "invoice"),
                q -> retrieve(docs, "A", Set.of("D1")));
        check(visible, List.of("A invoice paid"));
        System.out.println("scoped cache for A=" + visible);
        var revoked = cache.computeIfAbsent(new Key("A", "alice", 2, "invoice"),
                q -> retrieve(docs, "A", Set.of())); // new policy version, document permission revoked
        check(revoked, List.of());
        System.out.println("after ACL version change=" + revoked);
    }
}
