# Nested Classes demos

Примеры к теме Java › Nested Classes. JDK 21, без зависимостей.

```bash
cd examples/nested-classes
java LeakDemo.java   # какой слушатель удерживает 40 МБ отчёта, this в анонимном классе и лямбде, val$x
./javap.sh           # this$0, NestHost/NestMembers, лямбда через invokedynamic
```
