# Web demos

Запускаемые примеры к темам раздела Spring › Web. Нужны JDK 21 и Maven.
Зависимости зафиксированы в `pom.xml`: встроенный Tomcat 11.0.26, Spring Framework 7.0.9, Jackson 3.1.5, Spring Boot 4.1.1.

```bash
cd examples/web
./run.sh PoolDemo
./run.sh PoolDemo virtual
```

Скрипт собирает classpath через Maven и запускает файл через Java source launcher, то есть без флага компилятора `-parameters`. `logback.xml` оставляет от фреймворков только ошибки. Демо поднимают Tomcat на порту 18080 (PoolDemo ещё «банк» на 18081), сами отправляют запросы и останавливаются.

| Файл | Тема | Что показывает |
|---|---|---|
| `PoolDemo.java` | Servlet & Tomcat | соединения не занимают потоки, медленные запросы занимают; очередь при двух потоках и то же на виртуальных |
| `LifecycleDemo.java` | Servlet & Tomcat | ленивый `init()`, один экземпляр сервлета на все потоки, гонка на поле |
| `FilterDemo.java` | Servlet & Tomcat | вложенность фильтров, остановка цепочки, потерянный заголовок после коммита ответа |
| `ThreadLocalDemo.java` | Servlet & Tomcat | утечка ThreadLocal между запросами через поток пула и исправление через `finally` |
| `MvcTraceDemo.java` | Spring MVC | DispatcherServlet на голом Tomcat: его стратегии, таблица маршрутов, путь запроса через фильтр, интерцептор и конвертеры |
| `MvcStatusDemo.java` | Spring MVC | какой этап даёт 404, 405, 415, 406 и 400 и каким исключением |
| `MvcArgumentsDemo.java` | Spring MVC | забытый `@RequestBody`, имена без `-parameters`, String в `@RestController` и `@Controller` |
| `MvcBootDemo.java` | Spring MVC | то же в Spring Boot: ленивый DispatcherServlet, его маппинги, 404 через обработчик статики |
