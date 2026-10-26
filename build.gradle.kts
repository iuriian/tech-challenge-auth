plugins {
    kotlin("jvm") version "2.2.20"
    id("com.gradleup.shadow") version "9.2.2"
}

group = "br.com.fiap.oficina"
version = "1.0.0"

repositories { mavenCentral() }

val invoker: Configuration by configurations.creating

dependencies {
    // Fornecido pelo runtime do Cloud Functions — não vai no JAR
    compileOnly("com.google.cloud.functions:functions-framework-api:1.1.4")

    implementation(platform("io.insert-koin:koin-bom:4.1.1"))
    implementation("io.insert-koin:koin-core")
    implementation("com.nimbusds:nimbus-jose-jwt:10.5")
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.postgresql:postgresql:42.7.8")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.20.0")

    // Servidor local para ./gradlew runFunction
    invoker("com.google.cloud.functions.invoker:java-function-invoker:1.4.1")

    testImplementation("com.google.cloud.functions:functions-framework-api:1.1.4")
    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.insert-koin:koin-test")
    testImplementation("com.lemonappdev:konsist:0.17.3")
}

kotlin { jvmToolchain(21) }

tasks.test { useJUnitPlatform() }

tasks.shadowJar {
    archiveClassifier.set("all")
    mergeServiceFiles()   // preserva META-INF/services do driver JDBC
}

tasks.register<JavaExec>("runFunction") {
    group = "application"
    description = "Sobe a função localmente na porta 8081"
    mainClass.set("com.google.cloud.functions.invoker.runner.Invoker")
    classpath = files(invoker) + sourceSets["main"].runtimeClasspath
    args("--target", "br.com.fiap.oficina.auth.presentation.AuthFunction", "--port", "8081")
}