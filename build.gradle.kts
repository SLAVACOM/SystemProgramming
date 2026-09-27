plugins {
    kotlin("jvm") version "2.1.0"
    id("org.openjfx.javafxplugin") version "0.1.0"
    application
}

group = "org.slavacom"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

javafx {
    version = "21.0.4"
    modules = listOf("javafx.controls")
}

application {
    mainClass.set("org.slavacom.zasm.ui.MainAppKt")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}