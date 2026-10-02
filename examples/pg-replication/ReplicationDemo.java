import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLWarning;

// Запуск: ./setup.sh, затем java -cp "$(cat target/classpath.txt)" ReplicationDemo.java
// Демо разрывает сеть репликации, убивает primary и повышает реплику командами docker.
public class ReplicationDemo {
    static final String PRIMARY = "jdbc:postgresql://localhost:55441/postgres?user=postgres&password=pw";
    static final String REPLICA = "jdbc:postgresql://localhost:55442/postgres?user=postgres&password=pw";

    public static void main(String[] args) throws Exception {
        try (Connection p = DriverManager.getConnection(PRIMARY); Connection r = DriverManager.getConnection(REPLICA)) {
            exec(p, "drop table if exists account; create table account(id text primary key, balance bigint); insert into account values ('acc-1', 100)");
            Thread.sleep(500);
            // Реплика применяет WAL с задержкой 500 мс — как реплика под нагрузкой.
            exec(r, "alter system set recovery_min_apply_delay = '500ms'"); exec(r, "select pg_reload_conf()");
            Thread.sleep(300);

            for (String mode : new String[] {"local", "remote_apply"}) {
                exec(p, "set synchronous_commit = " + mode);
                long t = System.nanoTime();
                exec(p, "update account set balance = balance - 30 where id = 'acc-1'");
                long commitMs = (System.nanoTime() - t) / 1_000_000;
                long onPrimary = scalar(p, "select balance from account where id = 'acc-1'");
                long onReplica = scalar(r, "select balance from account where id = 'acc-1'");
                System.out.printf("== synchronous_commit=%-12s commit %3d ms; primary %d, replica right after %d%n", mode, commitMs, onPrimary, onReplica);
            }
            exec(p, "set synchronous_commit = local");
            Thread.sleep(800);
            var rs = p.createStatement().executeQuery("select application_name, sync_state, replay_lag from pg_stat_replication");
            rs.next();
            System.out.println("== pg_stat_replication: " + rs.getString(1) + " " + rs.getString(2) + ", replay_lag " + rs.getString(3));
            try { exec(r, "update account set balance = 0"); }
            catch (SQLException e) { System.out.println("== write on replica: " + e.getSQLState() + " " + e.getMessage().lines().findFirst().orElse("")); }
            exec(r, "alter system reset recovery_min_apply_delay"); exec(r, "select pg_reload_conf()");
        }

        // ---------- синхронный коммит ждёт недоступную реплику ----------
        docker("network", "disconnect", "pg-repl", "pg-replica");   // Сетевой разрыв между primary и репликой.
        try (Connection p = DriverManager.getConnection(PRIMARY); Connection admin = DriverManager.getConnection(PRIMARY)) {
            exec(p, "set synchronous_commit = on");
            var t = Thread.ofPlatform().start(() -> {
                try {
                    var st = p.createStatement();
                    st.execute("update account set balance = 10 where id = 'acc-1'");
                    for (SQLWarning w = st.getWarnings(); w != null; w = w.getNextWarning())
                        System.out.println("   warning: " + w.getMessage());
                } catch (SQLException e) { System.out.println("   " + e.getMessage()); }
            });
            Thread.sleep(3000);
            System.out.println("== synchronous_commit=on, replica unreachable: commit still waiting after 3 s: " + t.isAlive());
            exec(admin, "select pg_cancel_backend(pid) from pg_stat_activity where wait_event = 'SyncRep'");
            t.join();
            System.out.println("   balance on primary after cancel: " + scalar(admin, "select balance from account where id = 'acc-1'"));
        }

        // ---------- асинхронный коммит теряется при failover ----------
        try (Connection p = DriverManager.getConnection(PRIMARY)) {
            exec(p, "set synchronous_commit = local");
            exec(p, "update account set balance = 999 where id = 'acc-1'");
            System.out.println("== async commit while replica is cut off: primary balance " + scalar(p, "select balance from account where id = 'acc-1'"));
        }
        docker("kill", "pg-primary");
        docker("network", "connect", "pg-repl", "pg-replica");
        try (Connection r = DriverManager.getConnection(REPLICA)) {
            exec(r, "select pg_promote()");
            Thread.sleep(1000);
            System.out.println("== after promote, new primary balance: " + scalar(r, "select balance from account where id = 'acc-1'")
                    + ", in recovery: " + scalar(r, "select case when pg_is_in_recovery() then 1 else 0 end"));
        }
    }

    static void exec(Connection c, String sql) throws SQLException { c.createStatement().execute(sql); }

    static long scalar(Connection c, String sql) throws SQLException {
        var rs = c.createStatement().executeQuery(sql); rs.next(); return rs.getLong(1);
    }

    static void docker(String... args) throws Exception {
        String[] cmd = new String[args.length + 1];
        cmd[0] = "docker";
        System.arraycopy(args, 0, cmd, 1, args.length);
        new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor();
        System.out.println("-- docker " + String.join(" ", args));
    }
}
