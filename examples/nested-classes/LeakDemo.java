import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.*;

// Запуск: java LeakDemo.java   (JDK 21, без зависимостей)
public class LeakDemo {
    // Глобальная шина событий живёт всё время работы приложения.
    static final List<Consumer<String>> BUS = new ArrayList<>();

    static class ReportJob {
        final long[] rows = new long[5_000_000];                       // ~40 МБ данных отчёта
        void subscribeInner()     { BUS.add(new Listener()); }                 // внутренний, читает поле отчёта
        void subscribeIdleInner() { BUS.add(new IdleListener()); }             // внутренний, внешний объект не трогает
        void subscribeAnonymous() { BUS.add(new Consumer<String>() {           // анонимный, читает поле отчёта
                                        public void accept(String e) { rows[0]++; } }); }
        void subscribeNested()    { BUS.add(new StaticListener()); }           // статический вложенный
        void subscribeLambda()    { BUS.add(e -> System.out.print("")); }      // лямбда без this
        void subscribeLambdaThis(){ BUS.add(e -> rows[0]++); }                 // лямбда, читающая поле
        class Listener implements Consumer<String> { public void accept(String e) { rows[0]++; } }
        class IdleListener implements Consumer<String> { public void accept(String e) { } }
        static class StaticListener implements Consumer<String> { public void accept(String e) { } }
    }

    static boolean collected(Consumer<ReportJob> subscribe) throws InterruptedException {
        ReportJob job = new ReportJob();
        subscribe.accept(job);
        WeakReference<ReportJob> ref = new WeakReference<>(job);
        job = null;                                                    // отчёт больше не нужен
        for (int i = 0; i < 5 && ref.get() != null; i++) { System.gc(); Thread.sleep(50); }
        return ref.get() == null;
    }

    interface Fee { long of(long amount); }

    public static void main(String[] args) throws Exception {
        System.out.println("== listener on a global bus: is the 40 MB job collected?");
        System.out.println("inner class using the job:     " + collected(ReportJob::subscribeInner));
        System.out.println("anonymous class using the job: " + collected(ReportJob::subscribeAnonymous));
        System.out.println("lambda using a field:          " + collected(ReportJob::subscribeLambdaThis));
        System.out.println("inner class not using it:      " + collected(ReportJob::subscribeIdleInner) + " (javac 18+ drops unused this$0)");
        System.out.println("static nested class:           " + collected(ReportJob::subscribeNested));
        System.out.println("lambda without this:           " + collected(ReportJob::subscribeLambda));
        BUS.clear();

        System.out.println("== what 'this' means");
        new LeakDemo().thisDemo();

        System.out.println("== captured local variable is a copied field");
        long rate = 15;
        Fee fee = new Fee() { public long of(long amount) { return amount * rate / 1000; } };
        for (Field f : fee.getClass().getDeclaredFields())
            System.out.println("anonymous class field: " + f.getType() + " " + f.getName());
        System.out.println("fee.of(10_000) = " + fee.of(10_000));
    }

    @Override public String toString() { return "LeakDemo instance"; }

    void thisDemo() {
        Runnable anon = new Runnable() {
            public void run() { System.out.println("anonymous: this = " + this.getClass().getName()); }
        };
        Runnable lambda = () -> System.out.println("lambda:    this = " + this);
        anon.run();
        lambda.run();
    }
}
