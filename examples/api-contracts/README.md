# API contracts demos

Примеры к теме Backend › API › OpenAPI & Contracts. Нужны JDK 21, Maven и Docker.

```bash
cd examples/api-contracts
./run.sh ContractDemo   # Spring Boot 4.1 + springdoc 3.1: пишет out/v1.json и out/v2.json
docker run --rm -v "$PWD/out":/specs:ro tufin/oasdiff breaking /specs/v1.json /specs/v2.json
```
