import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "1.9.22"
    id("org.jetbrains.compose") version "1.6.0"
    kotlin("plugin.serialization") version "1.9.22"
}

group = "battleship"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    /**
     * ---------------------------------------------------------------------------------------------
     * Зависимость JUnit Platform Launcher.
     *
     * Необходима Gradle для запуска тестов через JUnit Platform.
     * ---------------------------------------------------------------------------------------------
     */
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)   // ← добавить
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}

/**
 * =============================================================================================
 * Настройка запуска тестов.
 *
 * Используется JUnit Platform для выполнения unit-тестов.
 * =============================================================================================
 */
tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "battleship.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "BattleshipAdmin"
            packageVersion = "1.0.0"
        }
    }
}
