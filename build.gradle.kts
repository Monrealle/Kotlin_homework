plugins {
    kotlin("jvm") version "2.3.21"
    id("org.jetbrains.compose") version "1.11.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
}

group = "battleship"
version = "1.0.0"

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation(compose.desktop.currentOs)

    /*
     * Material 3 подключается напрямую.
     *
     * compose.material3 является устаревшим alias,
     * поэтому используем актуальный artifact.
     */
    implementation("org.jetbrains.compose.material3:material3:1.9.0")

    implementation(
        "org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0"
    )

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(17)
}

/**
 * =============================================================================================
 * Настройка GUI приложения.
 *
 * Compose Desktop автоматически создаёт задачу `run`.
 * =============================================================================================
 */
compose.desktop {
    application {
        mainClass = "battleship.presentation.gui.GuiMainKt"

        nativeDistributions {
            packageName = "battleship-assistant"
            packageVersion = project.version.toString()
        }
    }
}

/**
 * =============================================================================================
 * Запуск консольной версии приложения.
 *
 * Консоль использует in-memory репозитории.
 * =============================================================================================
 */
tasks.register<JavaExec>("console") {
    group = "application"
    description = "Запускает консольную версию с in-memory хранилищем"

    dependsOn(tasks.named("classes"))

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("battleship.MainKt")

    standardInput = System.`in`
}

/**
 * =============================================================================================
 * Настройка тестов.
 * =============================================================================================
 */
tasks.test {
    useJUnitPlatform()
}

/**
 * =============================================================================================
 * Настройка JAR.
 *
 * JAR запускает GUI-версию приложения.
 * =============================================================================================
 */
tasks.jar {
    archiveFileName.set("battleship-assistant.jar")

    manifest {
        attributes["Main-Class"] = "battleship.presentation.gui.GuiMainKt"
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
