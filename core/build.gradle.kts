import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        // Match the Kotlin stdlib bundled with the lowest supported IDE (2025.3 ships 2.2).
        apiVersion = KotlinVersion.KOTLIN_2_2
        languageVersion = KotlinVersion.KOTLIN_2_2
    }
}

dependencies {
    // Provided by the IDE at runtime (see kotlin.stdlib.default.dependency in gradle.properties).
    compileOnly(kotlin("stdlib"))

    testImplementation(kotlin("stdlib"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // Lets NoNetworkTest scan exactly the compiled production classes of this module.
    val mainClasses = sourceSets.main.get().output.classesDirs
    inputs.files(mainClasses).withPropertyName("mainClasses")
    val mainClassesPath = mainClasses.asPath
    systemProperty("octet.core.mainClasses", mainClassesPath)
    systemProperty("octet.core.mainSources", layout.projectDirectory.dir("src/main").asFile.absolutePath)
}
