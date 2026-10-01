# Web demos

Запускаемые примеры к темам раздела Spring › Web. Нужны JDK 21 и Maven.
Зависимости зафиксированы в `pom.xml`: встроенный Tomcat 11.0.26, Spring Framework 7.0.9, Jackson 3.1.5, Spring Boot 4.1.1, Hibernate Validator 9.1.3, Apache HttpClient 5.6.4, Reactor Netty 1.3.7.

```bash
cd examples/web
./run.sh PoolDemo
./run.sh PoolDemo virtual
```

Скрипт собирает classpath через Maven, компилирует файл `javac` без флага `-parameters` в `target/<Demo>` и запускает. `logback.xml` оставляет от фреймворков только ошибки. Демо поднимают Tomcat на порту 18080 (PoolDemo ещё «банк» на 18081), сами отправляют запросы и останавливаются.

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
| `ValidationDemo.java` | Validation & Errors | валидатор и каскад `@Valid`, проверка тела и параметров в MVC, `@Validated` на контроллере и сервисе |
| `ErrorHandlingDemo.java` | Validation & Errors | `@ExceptionHandler` по ближайшему типу, локальный обработчик, ProblemDetail, исключение фильтра; `naive` — catch-all на Exception |
| `ClientDemo.java` | HTTP Clients | `RestClient.create()` без таймаутов против билдера Boot, ошибки 4xx и `onStatus`, HTTP-интерфейс `@ImportHttpServices` |
| `ClientPoolDemo.java` | HTTP Clients | пул Apache HttpClient на 5 соединений к хосту против пула на 20 и JDK HttpClient |
| `RetryDemo.java` | HTTP Clients | `@Retryable` из Spring 7 с паузами; тройное списание без ключа идемпотентности и одно с ключом |
| `ReactiveLoopDemo.java` | WebFlux | два потока event loop: `Mono.delay`, блокирующий `sleep` и вынос в `boundedElastic` |
| `ReactorBasicsDemo.java` | WebFlux | ленивость Mono, `publishOn` и `subscribeOn`, ThreadLocal против Context, backpressure через `request(n)` |
