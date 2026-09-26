plugins {
    kotlin("jvm") version "2.3.21"
    application
}

group = "battleship"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("battleship.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveFileName.set("battleship-assistant.jar")
    manifest {
        attributes["Main-Class"] = "battleship.MainKt"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { file ->
        if (file.isDirectory) file else zipTree(file)
    })
}
