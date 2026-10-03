FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
COPY App.java .
RUN javac -d out App.java

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /src/out .
ENTRYPOINT ["java", "App"]
