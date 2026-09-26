import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellij.platform)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        apiVersion = KotlinVersion.KOTLIN_2_2
        languageVersion = KotlinVersion.KOTLIN_2_2
    }
}

dependencies {
    implementation(project(":core"))

    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        bundledPlugin("com.intellij.modules.json")
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation(libs.junit4)
    testImplementation(libs.opentest4j)
}

intellijPlatform {
    // No settings pages yet; skip the slow headless IDE run that indexes them.
    buildSearchableOptions = false
    // Kotlin only, no GUI Designer forms: nothing to instrument. The task also fails on some local
    // JDKs, e.g. a Microsoft JDK in ~/.jdks on Windows ("...\Packages does not exist").
    instrumentCode = false

    pluginConfiguration {
        version = project.version.toString()
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

// Paid features stay unlocked in the sandbox IDE and in tests (see CardwireLicense.DEV_PROPERTY).
tasks {
    runIde {
        jvmArgs("-Dcardwire.license.dev=true")
    }
    test {
        systemProperty("cardwire.license.dev", "true")
    }
}
