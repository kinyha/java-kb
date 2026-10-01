# Web demos

Запускаемые примеры к темам раздела Spring › Web. Нужны JDK 21 и Maven.
Зависимости зафиксированы в `pom.xml`: встроенный Tomcat 11.0.26.

```bash
cd examples/web
./run.sh PoolDemo
./run.sh PoolDemo virtual
```

Скрипт собирает classpath через Maven и запускает файл через Java source launcher. Демо поднимают Tomcat на порту 18080 (PoolDemo ещё «банк» на 18081), сами отправляют запросы и останавливаются.

| Файл | Тема | Что показывает |
|---|---|---|
| `PoolDemo.java` | Servlet & Tomcat | соединения не занимают потоки, медленные запросы занимают; очередь при двух потоках и то же на виртуальных |
| `LifecycleDemo.java` | Servlet & Tomcat | ленивый `init()`, один экземпляр сервлета на все потоки, гонка на поле |
| `FilterDemo.java` | Servlet & Tomcat | вложенность фильтров, остановка цепочки, потерянный заголовок после коммита ответа |
| `ThreadLocalDemo.java` | Servlet & Tomcat | утечка ThreadLocal между запросами через поток пула и исправление через `finally` |
