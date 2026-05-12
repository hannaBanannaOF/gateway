# --- ESTÁGIO 1: Build (Onde a mágica acontece) ---
FROM registry.access.redhat.com/ubi8/openjdk-21:1.20 AS builder

USER root
WORKDIR /builder

# 1. Copia apenas os arquivos do wrapper e configurações primeiro
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle gradle.properties ./

# 2. Garante a permissão que estava dando erro e baixa dependências
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon

# 3. Copia o código fonte e gera o JAR
COPY src src
RUN ./gradlew clean build -x test --no-daemon


# --- ESTÁGIO 2: Runtime (A imagem final, leve e segura) ---
FROM registry.access.redhat.com/ubi8/openjdk-21:1.20

WORKDIR /app

# Copia o JAR gerado no estágio 'builder' para a imagem final
COPY --from=builder /builder/build/libs/gateway-1.0.0.jar /app/app.jar

# Define o usuário padrão da imagem UBI para segurança
USER 185

ENTRYPOINT ["java", "-jar", "/app/app.jar"]