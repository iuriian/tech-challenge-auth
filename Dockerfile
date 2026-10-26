# A imagem só empacota: o fat JAR com o servidor Ktor embutido sai do build do
# Gradle (./gradlew shadowJar).
FROM gcr.io/distroless/java21-debian12:nonroot

WORKDIR /app

# O Main-Class do manifesto é a classe do main() em Servidor.kt — ver mainClass
# no build.gradle.kts.
COPY build/libs/*-all.jar app.jar

ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:MaxRAMPercentage=75"

# O servidor escuta em $PORT (8080 por padrão), que é o contrato do Cloud Run.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
