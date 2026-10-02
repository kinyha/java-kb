# API styles demos

Примеры к теме Backend › API › gRPC, GraphQL, SOAP. Нужны JDK 21 и Maven.

```bash
cd examples/api-styles
./run.sh GrpcDemo      # gRPC 1.84 на Netty: unary, статус, deadline, стриминг
./run.sh GraphQlDemo   # graphql-java 26.1: N+1 и DataLoader
```

GrpcDemo работает без кодогенерации: сообщения — строки, маршаллер свой; транспорт настоящий HTTP/2.
