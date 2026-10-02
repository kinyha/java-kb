import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

// Запуск: PostgreSQL 17 на localhost:55432 (см. README), затем
// java -cp "$(cat target/classpath.txt)" PlpgsqlDemo.java
public class PlpgsqlDemo {
    static final String URL = "jdbc:postgresql://localhost:55432/postgres?user=postgres&password=pw";

    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(URL)) {
            exec(c, """
                    drop table if exists account, account_audit cascade;
                    create table account(id int primary key, balance bigint not null check (balance >= 0));
                    insert into account select g, 1000000 from generate_series(1, 1000) g;
                    create table account_audit(id bigserial, account_id int, old_balance bigint, new_balance bigint, at timestamptz default now());

                    create or replace function transfer(p_from int, p_to int, p_amount bigint) returns bigint
                    language plpgsql as $$
                    declare v_balance bigint;
                    begin
                        -- Блокируем счета в порядке id, чтобы встречные переводы не попали в deadlock.
                        perform 1 from account where id in (p_from, p_to) order by id for update;
                        select balance into v_balance from account where id = p_from;
                        if v_balance < p_amount then
                            raise exception 'insufficient funds on account %', p_from using errcode = 'P0901';
                        end if;
                        update account set balance = balance - p_amount where id = p_from;
                        update account set balance = balance + p_amount where id = p_to;
                        return v_balance - p_amount;
                    end $$;
                    """);
            roundTrips(c);
            customError(c);
            trigger(c);
            cursor(c);
        }
    }

    // ---------- один вызов функции против четырёх запросов из приложения ----------
    static void roundTrips(Connection c) throws SQLException {
        int n = 2000;
        c.setAutoCommit(false);
        var lock = c.prepareStatement("select 1 from account where id in (?, ?) order by id for update");
        var debit = c.prepareStatement("update account set balance = balance - ? where id = ? returning balance");
        var credit = c.prepareStatement("update account set balance = balance + ? where id = ?");
        long t = System.nanoTime();
        for (int i = 0; i < n; i++) {
            int a = i % 1000 + 1, b = (i + 1) % 1000 + 1;
            lock.setInt(1, a); lock.setInt(2, b); lock.executeQuery();
            debit.setLong(1, 1); debit.setInt(2, a); debit.executeQuery();
            credit.setLong(1, 1); credit.setInt(2, b); credit.executeUpdate();
            c.commit();
        }
        long app = (System.nanoTime() - t) / 1_000_000;
        var call = c.prepareStatement("select transfer(?, ?, ?)");
        t = System.nanoTime();
        for (int i = 0; i < n; i++) {
            call.setInt(1, i % 1000 + 1); call.setInt(2, (i + 1) % 1000 + 1); call.setLong(3, 1);
            call.executeQuery();
            c.commit();
        }
        long fn = (System.nanoTime() - t) / 1_000_000;
        c.setAutoCommit(true);
        System.out.printf("== %d transfers: from the app (3 statements + commit) %d ms, one function call + commit %d ms%n", n, app, fn);
    }

    // ---------- ошибка с собственным SQLSTATE ----------
    static void customError(Connection c) {
        try {
            exec(c, "select transfer(1, 2, 999999999)");
        } catch (SQLException e) {
            System.out.println("== transfer over balance -> SQLState " + e.getSQLState() + ": " + e.getMessage().lines().findFirst().orElse(""));
        }
    }

    // ---------- триггер аудита и его цена ----------
    static void trigger(Connection c) throws SQLException {
        long t = System.nanoTime();
        exec(c, "update account set balance = balance + 1");
        long without = (System.nanoTime() - t) / 1_000;
        exec(c, """
                create or replace function audit_balance() returns trigger language plpgsql as $$
                begin
                    if new.balance <> old.balance then
                        insert into account_audit(account_id, old_balance, new_balance) values (old.id, old.balance, new.balance);
                    end if;
                    return new;
                end $$;
                create trigger account_audit_trg after update on account for each row execute function audit_balance();
                """);
        t = System.nanoTime();
        exec(c, "update account set balance = balance + 1");
        long with = (System.nanoTime() - t) / 1_000;
        var rs = c.createStatement().executeQuery("select count(*) from account_audit");
        rs.next();
        System.out.printf("== update 1000 accounts: without trigger %.1f ms, with row trigger %.1f ms, audit rows %d%n", without / 1000.0, with / 1000.0, rs.getLong(1));
    }

    // ---------- курсор: setFetchSize читает порциями ----------
    static void cursor(Connection c) throws SQLException {
        String q = "select g, md5(g::text) from generate_series(1, 1000000) g";
        for (int fetch : new int[] {0, 1000}) {
            System.gc();
            Runtime rt = Runtime.getRuntime();
            long before = rt.totalMemory() - rt.freeMemory();
            c.setAutoCommit(fetch == 0);                       // Курсор драйвер открывает только в транзакции.
            var st = c.createStatement();
            st.setFetchSize(fetch);
            long t = System.nanoTime();
            var rs = st.executeQuery(q);
            rs.next();
            long firstRowMs = (System.nanoTime() - t) / 1_000_000;
            long peak = rt.totalMemory() - rt.freeMemory() - before;
            int rows = 1;
            while (rs.next()) rows++;
            System.out.printf("== read 1M rows, fetchSize %4d: first row after %4d ms, heap growth at first row ~%d MB, rows %d%n",
                    fetch, firstRowMs, peak / 1_000_000, rows);
            rs.close(); st.close();
            if (!c.getAutoCommit()) c.commit();
        }
        c.setAutoCommit(true);
    }

    static void exec(Connection c, String sql) throws SQLException { c.createStatement().execute(sql); }
}
