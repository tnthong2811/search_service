# syntax=docker/dockerfile:1.7
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests package

RUN JAR_FILE=$(ls target/*.jar | grep -v '\.original$' | head -n1) && cp "$JAR_FILE" app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache busybox-extras && \
    addgroup -S spring && adduser -S spring -G spring

COPY --from=build /build/dependencies/ ./
COPY --from=build /build/snapshot-dependencies/ ./
COPY --from=build /build/spring-boot-loader/ ./
COPY --from=build /build/application/ ./

RUN chown -R spring:spring /app
USER spring

ENV APP_PORT=8086

EXPOSE 8086

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD sh -c "nc -z 127.0.0.1 ${APP_PORT} || exit 1"

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:InitialRAMPercentage=50.0", "-XX:MaxRAMPercentage=75.0", "-XX:+UseG1GC", "-XX:+ExitOnOutOfMemoryError", "org.springframework.boot.loader.launch.JarLauncher"]