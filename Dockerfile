# syntax=docker/dockerfile:1

# =============================================================================
# Imagem da aplicação WorkFlow Crédito, em dois estágios (multi-stage).
#   Estágio 1 (build): JDK + Maven compilam e empacotam. Nada dele vai para a imagem final.
#   Estágio 2 (runtime): só a JRE 21 e a aplicação, rodando sem root.
# Construir: docker build -t workflow-app:dev .   (o compose faz isso com "build: .")
# =============================================================================

# ---------- Estágio 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Só o pom.xml primeiro. Enquanto ele não mudar, o Docker reaproveita esta camada
# e as dependências não são resolvidas de novo.
# O --mount=type=cache guarda o ~/.m2 entre builds SEM colocá-lo na imagem (BuildKit).
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:go-offline

# Agora o código. Os testes rodam no CI: aqui dentro não há Docker para o Testcontainers.
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp package -DskipTests

# Separa o jar em camadas (receita da documentação do Spring Boot 3.5, "Dockerfiles"):
# dependencies/ e spring-boot-loader/ mudam pouco; application/ muda a cada commit.
RUN cp target/*.jar application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --destination extraido

# ---------- Estágio 2: runtime ----------
# JRE (sem compilador e sem Maven) sobre Ubuntu 24.04. Traz curl, usado pelo healthcheck do compose.
FROM eclipse-temurin:21-jre-noble
WORKDIR /app

# Usuário de sistema, sem senha e sem pasta pessoal. Quem explorar uma falha na aplicação
# não vira root dentro do container.
RUN groupadd --system workflow \
 && useradd --system --gid workflow --no-create-home workflow

# Uma camada por grupo, da que menos muda para a que mais muda. Mudar uma linha de código
# só troca a última camada (alguns KB); o resto vem do cache no build, no push e no pull.
COPY --from=build /build/extraido/dependencies/ ./
COPY --from=build /build/extraido/spring-boot-loader/ ./
COPY --from=build /build/extraido/snapshot-dependencies/ ./
COPY --from=build /build/extraido/application/ ./

USER workflow
EXPOSE 8080

# Sem ajuste, a JVM usa só 25% da memória do container para o heap. 75% é um bom padrão para
# um container que só roda esta aplicação; o compose sobrescreve por serviço.
# ExitOnOutOfMemoryError: sem memória, o processo morre (e o "restart" do compose o sobe de novo),
# em vez de continuar vivo e quebrado.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# Forma exec (lista JSON): o java é o processo 1 e recebe o SIGTERM do "docker stop";
# o Spring Boot faz o desligamento gracioso (padrão desde o Boot 3.4).
ENTRYPOINT ["java", "-jar", "application.jar"]
