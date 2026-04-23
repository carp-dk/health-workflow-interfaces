plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.kotlin.serialization)
    `java-library`
}

group = "health.workflows"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    api(libs.kotlinx.serialization.json)
    implementation(kotlin("stdlib-jdk8"))

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation(libs.junit.jupiter.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
