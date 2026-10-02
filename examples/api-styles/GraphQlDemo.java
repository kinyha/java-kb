import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderRegistry;

// Запуск: ./run.sh GraphQlDemo   (graphql-java 26.1, java-dataloader 6.0)
public class GraphQlDemo {
    static final String SCHEMA = """
            type Query { payments: [Payment] }
            type Payment { id: ID, amount: Int, merchant: Merchant }
            type Merchant { id: ID, name: String }
            """;

    record Payment(String id, int amount, String merchantId) {}
    record Merchant(String id, String name) {}

    static final List<Payment> PAYMENTS = List.of(
            new Payment("p-1", 500, "m-1"), new Payment("p-2", 700, "m-2"), new Payment("p-3", 900, "m-1"),
            new Payment("p-4", 300, "m-3"), new Payment("p-5", 200, "m-2"));
    static final Map<String, Merchant> MERCHANTS = Map.of(
            "m-1", new Merchant("m-1", "Coffee"), "m-2", new Merchant("m-2", "Books"), "m-3", new Merchant("m-3", "Taxi"));

    static final AtomicInteger dbQueries = new AtomicInteger();
    static final List<String> sql = new ArrayList<>();

    // «База»: каждый вызов — один SQL-запрос.
    static Merchant merchantById(String id) {
        dbQueries.incrementAndGet();
        sql.add("select * from merchant where id = '" + id + "'");
        return MERCHANTS.get(id);
    }

    static List<Merchant> merchantsByIds(List<String> ids) {
        dbQueries.incrementAndGet();
        sql.add("select * from merchant where id in " + ids);
        return ids.stream().map(MERCHANTS::get).toList();
    }

    public static void main(String[] args) {
        String query = "{ payments { id merchant { name } } }"; // amount не запрошен — его не будет в ответе.
        for (boolean batched : new boolean[] {false, true}) {
            dbQueries.set(0);
            sql.clear();
            DataFetcher<Object> merchant = batched
                    ? env -> env.<String, Merchant>getDataLoader("merchants").load(((Payment) env.getSource()).merchantId())
                    : env -> merchantById(((Payment) env.getSource()).merchantId());
            var wiring = RuntimeWiring.newRuntimeWiring()
                    .type("Query", b -> b.dataFetcher("payments", env -> PAYMENTS))
                    .type("Payment", b -> b.dataFetcher("merchant", merchant))
                    .build();
            var schema = new SchemaGenerator().makeExecutableSchema(new SchemaParser().parse(SCHEMA), wiring);
            var registry = new DataLoaderRegistry();
            DataLoader<String, Merchant> loader = DataLoaderFactory.newDataLoader(
                    ids -> CompletableFuture.supplyAsync(() -> merchantsByIds(ids)));
            registry.register("merchants", loader);
            var result = GraphQL.newGraphQL(schema).build().execute(
                    ExecutionInput.newExecutionInput(query).dataLoaderRegistry(registry).build());
            System.out.println((batched ? "dataloader" : "naive") + ": " + dbQueries.get() + " merchant queries");
            sql.forEach(s -> System.out.println("  " + s));
            if (batched) System.out.println("response: " + result.getData());
        }
    }
}
