package kb.javatesting;

import java.sql.*;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@Testcontainers // fail when Docker is missing, don't silently disable these checks
class PaymentRepositoryIT {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.11-alpine")
            .withDatabaseName("payments").withUsername("kb").withPassword("kb");

    Connection open() throws SQLException {
        // Read real mapped connection settings AFTER extension has started the container.
        var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        assertThat(c.getAutoCommit()).isTrue();
        assertThat(c.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
        return c;
    }
    @BeforeEach void schemaAndCleanup() throws SQLException {
        try (var c = open(); var s = c.createStatement()) {
            s.execute("""
                    CREATE TABLE IF NOT EXISTS payment (
                      id text PRIMARY KEY,
                      amount bigint NOT NULL CHECK (amount > 0),
                      fee bigint NOT NULL CHECK (fee > 0)
                    )
                    """);
            s.execute("TRUNCATE payment"); // container reuse does not erase rows
            assertThat(count(c)).isZero();
        }
    }
    long insert(Connection c, String id, long amount) throws SQLException {
        try (var s = c.prepareStatement("INSERT INTO payment(id,amount,fee) VALUES (?,?,?) RETURNING fee")) {
            s.setString(1, id); s.setLong(2, amount); s.setLong(3, new FeePolicy().cents(amount));
            try (var r = s.executeQuery()) { r.next(); return r.getLong(1); }
        }
    }
    long count(Connection c) throws SQLException {
        try (var s = c.createStatement(); var r = s.executeQuery("SELECT count(*) FROM payment")) {
            r.next(); return r.getLong(1);
        }
    }
    @Test void executesRealSqlAndRejectsDuplicatePrimaryKey() throws Exception {
        try (var c = open()) { // JDBC default autocommit=true
            assertThat(insert(c, "P7", 101)).isEqualTo(3);
            var failure = Assertions.assertThrows(SQLException.class, () -> insert(c, "P7", 101));
            assertThat(failure.getSQLState()).isEqualTo("23505");
            assertThat(count(c)).isEqualTo(1);
            System.out.println("postgres: INSERT RETURNING fee=3, duplicate SQLState=23505, rows=1");
            try (var s = c.createStatement()) {
                var rejected = Assertions.assertThrows(SQLException.class,
                        () -> s.executeUpdate("INSERT INTO payment VALUES ('BAD',-1,1)"));
                assertThat(rejected.getSQLState()).isEqualTo("23514");
                System.out.println("postgres: amount CHECK SQLState=23514");
            }
        }
    }
    @Test void rollbackDoesNotUndoAnotherConnectionsCommit() throws Exception {
        try (var test = open(); var application = open()) {
            test.setAutoCommit(false);
            insert(test, "UNCOMMITTED", 101);
            assertThat(count(test)).isEqualTo(1);
            assertThat(count(application)).isZero();
            insert(application, "COMMITTED", 51); // independent autocommit
            assertThat(count(test)).isEqualTo(2); // READ COMMITTED sees the other commit on next SELECT
            test.rollback();
            assertThat(count(test)).isEqualTo(1);
            assertThat(count(application)).isEqualTo(1);
            System.out.println("postgres: test saw own row=1; other saw it=0; after test rollback committed rows=1");
        }
    }
    @Test void startsWithACleanDatabaseEvenInSharedContainer() throws Exception {
        try (var c = open()) {
            assertThat(count(c)).isZero();
            System.out.println("postgres: BeforeEach TRUNCATE -> rows=0");
        }
    }
}
