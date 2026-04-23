plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("health.workflows.server.MainKt")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":lib"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.kaml)
    implementation(libs.logback)

    testImplementation(libs.ktor.server.test.host)
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation(libs.junit.jupiter.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

/**
 * Generate a new API key and register it in config/keys.yaml.
 *
 * Usage:
 *   ./gradlew :server:generateKey -PuserId=nigel@carp.dk -Pname="Nigel Grech"
 *   ./gradlew :server:generateKey -PuserId=aware-service -Pname="Aware Adapter" -PconfigDir=path/to/config
 */
tasks.register<JavaExec>("generateKey") {
    group = "application"
    description = "Generate an API key and add it to config/keys.yaml. Pass -PuserId=<id> -Pname=<name>."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("health.workflows.server.auth.GenerateKeyKt")
    args = listOfNotNull(
        project.findProperty("userId") as String?,
        project.findProperty("name") as String?,
        project.findProperty("configDir") as String?,
    )
}
