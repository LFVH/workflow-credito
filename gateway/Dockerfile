# syntax=docker/dockerfile:1

# =============================================================================
# Imagem do gateway (Spring Cloud Gateway). Mesmo molde da imagem da aplicação.
# O contexto do build é a pasta gateway/: docker build -t workflow-gateway:dev ./gateway
# =============================================================================

# ---------- Estágio 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:go-offline

# Os testes do gateway rodam no CI (job "gateway") e no notebook.
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp package -DskipTests

RUN cp target/*.jar application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --destination extraido

# ---------- Estágio 2: runtime ----------
FROM eclipse-temurin:21-jre-noble
WORKDIR /app

RUN groupadd --system gateway \
 && useradd --system --gid gateway --no-create-home gateway

COPY --from=build /build/extraido/dependencies/ ./
COPY --from=build /build/extraido/spring-boot-loader/ ./
COPY --from=build /build/extraido/snapshot-dependencies/ ./
COPY --from=build /build/extraido/application/ ./

USER gateway

# No notebook o gateway usa a 8000 (application.yml); dentro do container, a 8080.
# Variável de ambiente vence o application.yml.
ENV SERVER_PORT=8080
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "-jar", "application.jar"]
