import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Indexes.*;
import static com.mongodb.client.model.Sorts.descending;
import static com.mongodb.client.model.Updates.*;

import com.mongodb.MongoWriteConcernException;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.bson.Document;

// Запуск: ./setup.sh, затем java -cp "$(cat target/classpath.txt)" MongoDemo.java
public class MongoDemo {
    public static void main(String[] args) throws Exception {
        try (var client = MongoClients.create("mongodb://localhost:27117/?directConnection=true")) {
            MongoDatabase db = client.getDatabase("pay");
            db.drop();
            embedding(db);
            indexes(db);
            transaction(client, db);
            writeConcern(db);
        }
    }

    // ---------- документ с вложенными возвратами и атомарное обновление ----------
    static void embedding(MongoDatabase db) {
        var payments = db.getCollection("payments");
        payments.insertOne(new Document("_id", "p-1").append("merchant", 7).append("amount", 500)
                .append("refunded", 0).append("refunds", new ArrayList<>()));
        // Обновление одного документа атомарно: условие и изменение вместе.
        // Возврат проходит, только если вместе с прошлыми не превысит сумму платежа (500).
        var r1 = payments.updateOne(and(eq("_id", "p-1"), lte("refunded", 500 - 200)),
                combine(push("refunds", new Document("id", "r-1").append("amount", 200)), inc("refunded", 200)));
        var r2 = payments.updateOne(and(eq("_id", "p-1"), lte("refunded", 500 - 350)),
                combine(push("refunds", new Document("id", "r-2").append("amount", 350)), inc("refunded", 350)));
        System.out.println("== refund 200: matched " + r1.getMatchedCount() + "; refund 350 more: matched " + r2.getMatchedCount());
        System.out.println("   " + payments.find(eq("_id", "p-1")).first().toJson());
    }

    // ---------- индексы и explain: ESR — равенство, сортировка, диапазон ----------
    static void indexes(MongoDatabase db) {
        MongoCollection<Document> c = db.getCollection("ledger");
        List<Document> batch = new ArrayList<>();
        for (int i = 0; i < 200_000; i++) {
            batch.add(new Document("merchant", i % 1000).append("amount", i % 5000).append("createdAt", i));
            if (batch.size() == 10_000) { c.insertMany(batch); batch.clear(); }
        }
        var filter = and(eq("merchant", 42), gt("amount", 2500));
        var sort = descending("createdAt");
        report("no index", c, filter, sort);
        c.createIndex(compoundIndex(ascending("merchant"), ascending("amount"), ascending("createdAt")));
        report("index (merchant, amount, createdAt)", c, filter, sort);
        c.dropIndexes();
        c.createIndex(compoundIndex(ascending("merchant"), ascending("createdAt"), ascending("amount")));
        report("index (merchant, createdAt, amount)", c, filter, sort);
    }

    static void report(String title, MongoCollection<Document> c, org.bson.conversions.Bson f, org.bson.conversions.Bson s) {
        Document ex = c.find(f).sort(s).limit(10).explain();
        Document stats = ex.get("executionStats", Document.class);
        String plan = ex.get("queryPlanner", Document.class).get("winningPlan", Document.class).toJson();
        System.out.printf("== %-36s docsExamined %6d, keysExamined %6d, in-memory SORT %s, %s%n", title + ":",
                stats.getInteger("totalDocsExamined"), stats.getInteger("totalKeysExamined"),
                plan.contains("\"SORT\""), plan.contains("COLLSCAN") ? "COLLSCAN" : "IXSCAN");
    }

    // ---------- транзакция на несколько документов ----------
    static void transaction(com.mongodb.client.MongoClient client, MongoDatabase db) {
        var acc = db.getCollection("accounts");
        acc.insertMany(List.of(new Document("_id", "a").append("balance", 100), new Document("_id", "b").append("balance", 0)));
        try (var session = client.startSession()) {
            session.withTransaction(() -> {
                acc.updateOne(session, eq("_id", "a"), inc("balance", -70));
                acc.updateOne(session, eq("_id", "b"), inc("balance", 70));
                return null;
            });
        }
        System.out.println("== transfer in a transaction: " + acc.find().into(new ArrayList<>()).stream().map(Document::toJson).toList());
    }

    // ---------- write concern: подтверждение большинства ----------
    static void writeConcern(MongoDatabase db) throws Exception {
        var c = db.getCollection("events");
        stop("mongo-2"); stop("mongo-3");
        var w1 = c.withWriteConcern(WriteConcern.W1);
        w1.insertOne(new Document("_id", "e-1").append("note", "w:1"));
        System.out.println("== two secondaries down, w:1 insert -> acknowledged");
        try {
            c.withWriteConcern(WriteConcern.MAJORITY.withWTimeout(2, TimeUnit.SECONDS))
                    .insertOne(new Document("_id", "e-2").append("note", "w:majority"));
        } catch (MongoWriteConcernException e) {
            System.out.println("== w:majority insert -> " + e.getWriteConcernError().getCodeName() + ": " + e.getWriteConcernError().getMessage());
        }
        System.out.println("   documents on primary now: " + c.countDocuments());
        start("mongo-2"); start("mongo-3");
    }

    static void stop(String n) throws Exception { run("docker", "stop", n); System.out.println("-- docker stop " + n); }
    static void start(String n) throws Exception { run("docker", "start", n); }
    static void run(String... cmd) throws Exception {
        new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor();
    }
}
