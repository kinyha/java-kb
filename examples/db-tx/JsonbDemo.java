import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

// Запуск: PostgreSQL 17 на localhost:55432 (см. README), затем
// java -cp "$(cat target/classpath.txt)" JsonbDemo.java
public class JsonbDemo {
    static final String URL = "jdbc:postgresql://localhost:55432/postgres?user=postgres&password=pw";
    static Connection c;

    public static void main(String[] args) throws Exception {
        c = DriverManager.getConnection(URL);
        exec("""
                set max_parallel_workers_per_gather = 0;
                drop table if exists payment_doc;
                create table payment_doc(
                    id bigint primary key,
                    merchant_id int not null,
                    doc jsonb not null,
                    check (doc ? 'currency' and (doc->>'amount')::bigint > 0));   -- инвариант внутри документа
                insert into payment_doc
                select g, g % 1000, jsonb_build_object(
                    'amount', g % 5000 + 1, 'currency', 'RUB',
                    'card', jsonb_build_object('brand', case when g % 3 = 0 then 'MIR' else 'VISA' end, 'country', case when g % 10 = 0 then 'KZ' else 'RU' end),
                    'tags', case when g % 50 = 0 then '["chargeback"]'::jsonb else '[]'::jsonb end)
                from generate_series(1, 500000) g;
                """);
        exec("analyze payment_doc");
        String q = "select count(*) from payment_doc where doc @> '{\"card\": {\"country\": \"KZ\"}, \"tags\": [\"chargeback\"]}'";
        System.out.println("== containment query on a document");
        plan(q);
        exec("create index on payment_doc using gin (doc jsonb_path_ops)");
        plan(q);

        exec("update payment_doc set doc = jsonb_set(doc, '{tags}', doc->'tags' || '\"refunded\"') where id = 50");
        var rs = c.createStatement().executeQuery("select doc->'tags', doc #>> '{card,brand}' from payment_doc where id = 50");
        rs.next();
        System.out.println("== partial update of one field: tags " + rs.getString(1) + ", card brand " + rs.getString(2));

        try { exec("insert into payment_doc values (900001, 1, '{\"amount\": -5, \"currency\": \"RUB\"}')"); }
        catch (SQLException e) { System.out.println("== bad document: " + e.getSQLState() + " " + e.getMessage().lines().findFirst().orElse("")); }

        rs = c.createStatement().executeQuery("""
                select m.merchant_id, count(*) from payment_doc m
                where doc @> '{"tags": ["chargeback"]}' group by 1 order by 2 desc, 1 limit 3""");
        System.out.print("== SQL over documents, top merchants by chargebacks:");
        while (rs.next()) System.out.print(" m-" + rs.getInt(1) + "=" + rs.getInt(2));
        System.out.println();
        c.close();
    }

    static void plan(String sql) throws SQLException {
        var rs = c.createStatement().executeQuery("explain (analyze, buffers, costs off, timing off) " + sql);
        String scan = null, buf = null, time = null;
        while (rs.next()) {
            String l = rs.getString(1).strip();
            if (scan == null && l.contains("Scan") && !l.contains("Bitmap Index")) scan = l.replace("->  ", "");
            if (buf == null && l.startsWith("Buffers")) buf = l;
            if (l.startsWith("Execution")) time = l;
        }
        System.out.println("   " + scan + "; " + buf + "; " + time);
    }

    static void exec(String sql) throws SQLException { c.createStatement().execute(sql); }
}
