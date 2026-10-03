# CI/CD demo

Пример к теме DevOps › CI/CD. Нужны JDK 21, Maven и Docker.

```bash
./examples/cicd/cicd.sh
```

Скрипт работает с временной копией examples/testing-strategy: сборка с пустым и прогретым кешем Maven, две сборки одного коммита с outputTimestamp и без, продвижение образа. Первый замер скачивает зависимости из сети. ci.yml — пример конвейера GitHub Actions; YAML проверен разбором, конвейер не запускался.
