# Spring Core: карта покрытия и проверка

Дата: 02.10.2026. Аудитория: Java middle → middle+/senior. Подача: `topic-design.md`.
Основа: Spring Framework Reference, исходники тега v7.0.9. Учебный ориентир: Spring Start Here, главы 2–6 (контекст → зависимости → scope → аспекты). Список Baeldung Spring Interview Questions использован для сверки вопросов, а не как источник поведения API.

Раздел состоит из семи тем. Сохранены spring-core, bean-scopes, bean-lifecycle, spring-aop, spring-events; добавлены spring-beans и spring-di. Boot configuration/properties, MVC, транзакции доступа к данным, scheduling и testing остаются соседними разделами. Core рассматривает их только в месте пересечения с контейнером.

## IoC & Context

Центральный вопрос: что именно контейнер собирает и чем управляет?

- IoC, DI и service locator → assembly, lookup; карточка ioc-di.
- Определение/экземпляр/proxy, new, граница управления DTO → definitions; bean-definition.
- Рекурсивная сборка, eager/lazy, refresh → lookup и context; исходники AbstractApplicationContext.refresh.
- BeanFactory/ApplicationContext, Environment/ResourceLoader/messages/events → context; factory-context. Properties подробно → Boot / Configuration & Profiles.
- Иерархия: lookup по имени, autowiring из предков, локальное перечисление → hierarchy; ContextDemo; context-hierarchy.
- Диагностика причин создания и ограничения → boundaries. Точный выбор → Dependency Resolution; готовность → Lifecycle.

Доказательства: ContextDemo, Java 21.0.11, Spring 7.0.9; реальный вывод в странице. ContextDemo теперь сравнивает два имени локального бина при компиляции без -parameters и с ним: ошибка неоднозначности против выбора родителя по имени аргумента. Утверждение без условия компиляции было ошибочным и исправлено. Настройка spring-boot-starter-parent сверена с официальной страницей Maven Plugin / Using. Схемы: реестр и граф ссылок; пять кадров создания singleton. Все кадры, dark/mobile и раскрытие карточки/вывода проверяются перед коммитом.

## Configuration & Beans

Центральный вопрос: как конфигурация превращается в определения и когда обычный вызов @Bean создаёт лишний объект?

- Stereotypes, scan boundaries и чтение метаданных → scanning; registration card.
- @Bean, имена/aliases, declared return type, new → factory-methods.
- Full/lite, CGLIB enhancement, static/final ограничения, injection через параметры → configuration; BeansDemo, full-lite card.
- @Import, ImportSelector/Registrar, XML и программная регистрация → composition.
- @Profile/@Conditional, Environment, placeholders и SpEL → conditions. Полный property precedence и binding → Boot / Configuration & Profiles.
- FactoryBean, &name, product cache, lifecycle и getObjectType → factories; factory-bean card.
- Scan conflicts и случайный импорт, границы декларативности → composition/conditions/factories.

Проверка реализации: ConfigurationClassEnhancer.BeanMethodInterceptor и AbstractBeanFactory.getObjectForBeanInstance, исходники v7.0.9. Учебный ориентир — Spring Start Here, главы 2–3; современное поведение — Reference, Java-based configuration, classpath scanning, container extension points.

## Dependency Resolution

Центральный вопрос: какую ссылку Spring передаст в injection point и почему?

- Constructor/setter/field, момент внедрения, final и optional setters → injection-style; constructor card.
- Тип/qualifier/generics, candidate eligibility → candidates и qualifiers; candidates card.
- Primary, Fallback (6.2+), Priority/name/default; version-sensitive shortcuts и -parameters → single-choice, deep; preference card.
- List/Map, Order, одинаковый qualifier на нескольких beans → collections; DiDemo.
- Optional, required=false, nullable, ObjectProvider getIfAvailable/getIfUnique/orderedStream, lazy → deferred; provider card.
- Resource by name, Autowired by type, unresolved/ambiguous errors → qualifiers/boundaries.
- Constructor cycles → Lifecycle, prototype retrieval → Scopes; не размазывать здесь их механизмы.

Источники: Reference, Annotation-based Container Configuration; исходники DefaultListableBeanFactory.doResolveDependency/determineAutowireCandidate. В 7.0.9 single selection включает primary/не-fallback, dependency name, suggested qualifier name, highest priority, default candidate и resolvable dependency; схема показывает понятный семантический случай, а не обещает неизменный порядок всех внутренних оптимизаций.

## Bean Scopes

Центральный вопрос: почему prototype внутри singleton остаётся тем же объектом и как получать зависимость нужного времени жизни?

Подача: от общего черновика платежа к графу удерживаемых ссылок; затем явный запрос через provider и подстановка объекта текущего HTTP-запроса. Reference иллюстрирует scope связями клиентов с экземплярами; в уроке добавляется время повторных обращений, чтобы различить новую сборку и повторный вызов метода.

- Singleton на имя/фабрику, разделяемое состояние и конкурентность → sharing; карта областей жизни.
- Prototype создаётся при запросе фабрики, прямое внедрение один раз → prototype; ScopesDemo и четыре кадра.
- ObjectProvider/ObjectFactory, один экземпляр на операцию, @Lookup и его ограничения → retrieval.
- Request/session/application/websocket, request binding, прокси и финальные методы → web; RequestScopeDemo, схема двух запросов.
- Инициализация и отсутствие автоматического destroy у prototype, владение ресурсом → cleanup.
- Потоки, сохранённые ссылки, stateless и явные параметры → boundaries. Механика AOP → AOP; полный порядок callbacks → Lifecycle.

Источники: Reference / Bean Scopes и Method Injection, API Lookup и RequestScope (7.0.9). Java 21: ScopesDemo сравнивает прямое внедрение/provider и init/destroy; RequestScopeDemo использует настоящую request scope с MockHttpServletRequest, без HTTP-сервера. Нагрузка сервера, сессии и WebSocket не эмулируются: их границы сверены с Reference.

## Bean Lifecycle

Центральный вопрос: когда объект действительно готов и что получает другой бин до этого момента?

Подача: вместо одной ленты терминов — состояние Gateway (clock/ready) и отдельный граф цикла A/B. В Reference callback-порядок сопоставлен со способом создания; опыт фиксирует конкретный порядок собственного BPP и CommonAnnotationBeanPostProcessor, а не обобщает его на все BPP.

- Определения до объектов: BDRPP/BFPP, static infrastructure Bean, раннее создание → definitions.
- Constructor → population → Aware → BPP before / PostConstruct → afterPropertiesSet → init → BPP after → publication → initialization; LifecycleDemo, схема состояния.
- Constructor cycle, early exposure, три singleton-cache и ранний proxy → cycles; CycleDemo, отдельная схема. Framework defaults true, Boot false проверены по исходнику и properties reference.
- SmartInitializingSingleton, ContextRefreshedEvent, SmartLifecycle/phases/start-stop и lazy → startup.
- PreDestroy/DisposableBean/destroyMethod, зависимые раньше зависимости, graceful/abrupt shutdown → shutdown.
- Диагностика BPP eligibility, внешние эффекты не откатываются вместе с refresh, готовность разных уровней → boundaries.

Источники: Reference / Customizing the Nature of a Bean, Container Extension Points; API BeanPostProcessor/Bean/SmartLifecycle; AbstractAutowireCapableBeanFactory 7.0.9 doCreateBean/initializeBean. LifecycleDemo и CycleDemo запускаются Java 21; примеры сети/пулов обсуждаются как границы, реальные внешние подключения не выполняются.

## AOP & Proxies

Центральный вопрос: через какую ссылку прошёл вызов и какая цепочка его окружала?

Подача: внешняя оболочка и отдельный target вместо идеи «аннотация меняет метод». Сопоставление двух proxy types на одном интерфейсе; затем отдельный опыт двух Around. Это разделяет геометрию вызова и порядок advice, который Reference объясняет через вложенность входа/выхода.

- Цель AOP, join point/pointcut/advice/advisor/aspect, регистрация → interception и selection.
- Ограничения Spring method execution; this/target/args/bean, AspectJ отличие → selection/deep.
- Around/proceed, before/returning/throwing/finally, замена результата и ошибки → chain; AdviceDemo и пять кадров вложенности.
- JDK/CGLIB, интерфейсный контракт/final/private/static, Objenesis и непрогретые поля прокси → proxy-types; ProxyDemo.
- Self invocation, init, new, extraction/self-reference/AopContext → self-invocation; схема внешнего и внутреннего вызова.
- Order и retry/transaction, диагностика AopUtils/Advised, matching и слои → boundaries.

Источники: Reference / Proxying Mechanisms, Declaring Advice, Declaring a Pointcut, @AspectJ support; Boot / AOP defaults. Оба примера Java 21 / 7.0.9. Final owner в CGLIB демонстрируется с присваиванием в конструкторе, чтобы javac не заменил чтение constant field на литерал. Отдельно отмечен bypass конструктора прокси в данном запуске; реальная БД/транзакции для примера порядка не используются.
