import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask

plugins {
    kotlin("jvm") version "2.2.20"
    application
    id("com.gradleup.shadow") version "9.2.2"
    id("com.diffplug.spotless") version "7.0.4"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    jacoco
}

group = "br.com.fiap.oficina"
version = "1.0.0"

repositories { mavenCentral() }

val ktlintVersion = "1.6.0"

dependencies {
    implementation(platform("io.ktor:ktor-bom:3.6.0"))
    implementation("io.ktor:ktor-server-core")
    // CIO: engine em coroutines puras, sem Netty. São menos classes para
    // carregar na partida, o que importa com min-instances=0 no Cloud Run.
    implementation("io.ktor:ktor-server-cio")
    implementation("io.ktor:ktor-server-status-pages")

    implementation(platform("io.insert-koin:koin-bom:4.1.1"))
    implementation("io.insert-koin:koin-core")
    implementation("com.nimbusds:nimbus-jose-jwt:10.5")
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.postgresql:postgresql:42.7.8")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.20.0")

    // O Ktor loga via SLF4J; a ponte entrega tudo ao java.util.logging, que é o
    // logger do resto do projeto e cai no stdout coletado pelo Cloud Logging.
    runtimeOnly("org.slf4j:slf4j-jdk14:2.0.20")

    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("io.insert-koin:koin-test")
    testImplementation("com.lemonappdev:konsist:0.17.3")
}

kotlin { jvmToolchain(21) }

application {
    // Classe gerada pelo Kotlin para o main() de Servidor.kt. É esse nome que o
    // shadowJar grava no Main-Class do manifesto — o Dockerfile só faz -jar.
    mainClass.set("br.com.fiap.oficina.auth.presentation.ServidorKt")
}

// ./gradlew run sobe o servidor local na 8081 e deixa a 8080 livre para o
// restante do stack rodando na máquina.
tasks.named<JavaExec>("run") {
    description = "Sobe o servidor localmente na porta 8081"
    environment("PORT", "8081")
}

jacoco { toolVersion = "0.8.13" }

// O relatório é subproduto da própria execução dos testes: medir cobertura num
// segundo ./gradlew test dobraria o tempo do pipeline sem medir nada diferente.
tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

// main() sobe um servidor de verdade e nenhum teste o executa sem subir o
// processo. O ServidorTest cobre o que dá para cobrir (porta e mainClass) por
// reflexão; manter a classe no denominador só empurraria o mínimo para baixo.
val classesMedidas =
    files(
        sourceSets.main
            .get()
            .output.classesDirs.files
            .map { fileTree(it) { exclude("**/presentation/ServidorKt*.class") } },
    )

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(classesMedidas)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

// Os mínimos ficam abaixo do medido de propósito: as lambdas suspend do Ktor e
// os blocos do Koin têm caminhos de retomada que o Jacoco nunca marca, então um
// teto apertado quebraria o build a cada rota nova sem nada a corrigir.
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(classesMedidas)

    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }

tasks.shadowJar {
    archiveClassifier.set("all")
    mergeServiceFiles() // preserva META-INF/services do driver JDBC e do Ktor
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
            "Dockerfile",
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
