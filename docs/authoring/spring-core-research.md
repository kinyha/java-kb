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

Доказательства: ContextDemo, Java 21.0.11, Spring 7.0.9; реальный вывод в странице. Неоднозначность parent/child с разными именами также получена запуском. Схемы: реестр и граф ссылок; пять кадров создания singleton. Все кадры, dark/mobile и раскрытие карточки/вывода проверяются перед коммитом.
