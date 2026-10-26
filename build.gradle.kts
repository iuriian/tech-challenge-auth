import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask

plugins {
    kotlin("jvm") version "2.2.20"
    id("com.gradleup.shadow") version "9.2.2"
    id("com.diffplug.spotless") version "7.0.4"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

group = "br.com.fiap.oficina"
version = "1.0.0"

repositories { mavenCentral() }

val ktlintVersion = "1.6.0"

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
    mergeServiceFiles() // preserva META-INF/services do driver JDBC
}

tasks.register<JavaExec>("runFunction") {
    group = "application"
    description = "Sobe a função localmente na porta 8081"
    mainClass.set("com.google.cloud.functions.invoker.runner.Invoker")
    classpath = files(invoker) + sourceSets["main"].runtimeClasspath
    args("--target", "br.com.fiap.oficina.auth.presentation.AuthFunction", "--port", "8081")
}

// Spotless é o único formatador do projeto e usa o ktlint como engine.
// O ruleset de formatação do detekt embute ktlint também e fica desligado de
// propósito, para não haver dois donos do mesmo arquivo.
//
// O estilo vem exclusivamente do .editorconfig. Não use editorConfigOverride
// aqui: ele vence o arquivo, e aí a IDE (que lê o .editorconfig) e o build
// passam a divergir sem aviso.
spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(ktlintVersion)
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target("*.gradle.kts")
        ktlint(ktlintVersion)
        trimTrailingWhitespace()
        endWithNewline()
    }

    format("demais") {
        target(
            "*.md",
            ".github/**/*.yml",
            "sql/**/*.sql",
            "terraform/**/*.tf",
            "terraform/**/*.tftpl",
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    source.setFrom("src/main/kotlin", "src/test/kotlin")
    baseline = file("$rootDir/config/detekt/detekt-baseline.xml")
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "21"
    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(false)
        sarif.required.set(false)
        md.required.set(false)
    }
}

tasks.withType<DetektCreateBaselineTask>().configureEach {
    jvmTarget = "21"
}

tasks.register("qualidade") {
    group = "verification"
    description = "Verifica formatação (Spotless/ktlint) e análise estática (detekt)"
    dependsOn("spotlessCheck", "detekt")
}
