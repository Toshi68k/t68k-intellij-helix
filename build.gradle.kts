import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.intellij.platform") version "2.18.1"
    id("dev.detekt") version "2.0.0-alpha.6"
}

group = "jp.titze.intellij"
version = providers.gradleProperty("pluginVersion").getOrElse("0.1.0")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
        vendor = JvmVendorSpec.AMAZON
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.2.4")
        testFramework(TestFrameworkType.Platform)
    }
    detektPlugins("dev.detekt:detekt-rules-ktlint-wrapper:2.0.0-alpha.6")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("org.junit.vintage:junit-vintage-engine:6.1.3")
    testImplementation("io.kotest:kotest-assertions-core:6.2.4") {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-jdk8")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-test")
    }
}

detekt {
    autoCorrect = true
    buildUponDefaultConfig = true
    config.setFrom(files("$projectDir/config/detekt/detekt.yml"))
}

tasks.test {
    useJUnitPlatform()
}

intellijPlatform {
    pluginConfiguration {
        id = "jp.titze.intellij.helix"
        name = "Helix Keymap"
        version = project.version.toString()
        vendor {
            name = "Thorsten Titze"
            url = "https://titze.jp"
        }
        description = """
            Complete Helix-style modal editing plugin for IntelliJ IDEA.
            Features selection-first paradigm, multi-caret operations, ActionManager delegation for native IDE navigation and refactoring, and status bar mode indication.
        """.trimIndent()
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }
        }
    }
}

kotlin {
    jvmToolchain(21)
}
