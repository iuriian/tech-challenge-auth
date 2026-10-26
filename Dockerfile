# A imagem só empacota: o fat JAR e o invoker saem do build do Gradle
# (./gradlew shadowJar, que já dispara copiarInvoker).
FROM gcr.io/distroless/java21-debian12:nonroot

WORKDIR /app

# Servidor do Functions Framework. É ele que mantém a função escrita contra
# com.google.cloud.functions.HttpFunction rodando como container no Cloud Run,
# sem precisar reescrever a camada de apresentação.
COPY build/invoker/ invoker/
COPY build/libs/*-all.jar app.jar

ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:MaxRAMPercentage=75"

# O Invoker lê a porta de $PORT (8080 por padrão), que é o contrato do Cloud Run.
EXPOSE 8080

ENTRYPOINT ["java", "-cp", "invoker/*", \
  "com.google.cloud.functions.invoker.runner.Invoker", \
  "--classpath", "app.jar", \
  "--target", "br.com.fiap.oficina.auth.presentation.AuthFunction"]
