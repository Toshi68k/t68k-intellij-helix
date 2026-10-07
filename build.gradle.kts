import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("java")
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.intellij.platform") version "2.18.1"
    id("org.jetbrains.changelog") version "2.4.0"
    id("dev.detekt") version "2.0.0-alpha.6"
}

group = "jp.titze.intellij"
version = providers.gradleProperty("pluginVersion").getOrElse("0.1.0")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion").getOrElse("2025.1.5"))
        testFramework(TestFrameworkType.Platform)
    }
    detektPlugins("dev.detekt:detekt-rules-ktlint-wrapper:2.0.0-alpha.6")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("org.junit.vintage:junit-vintage-engine:6.1.3")
    testImplementation("io.kotest:kotest-assertions-core:6.2.4") {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-jdk8")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-test")
    }
}

// Target IntelliJ 2025.1 (bundled JBR 21, Kotlin 2.1 stdlib)
kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        apiVersion.set(KotlinVersion.KOTLIN_2_1)
        languageVersion.set(KotlinVersion.KOTLIN_2_1)
    }
}

detekt {
    autoCorrect = true
    buildUponDefaultConfig = true
    config.setFrom(files("$projectDir/config/detekt/detekt.yml"))
}

tasks.named("detektTest") {
    enabled = false
}

tasks.test {
    useJUnitPlatform()
}

changelog {
    groups.empty()
    repositoryUrl = "https://github.com/Toshi68k/t68k-intellij-helix"
}

intellijPlatform {
    pluginConfiguration {
        id = "jp.titze.helix"
        name = "Helix Keymap (T68k)"
        version = project.version.toString()
        vendor {
            name = "Thorsten Titze"
            url = "https://github.com/Toshi68k"
        }
        ideaVersion {
            sinceBuild = "251"
            untilBuild = provider { null }
        }
        changeNotes = provider {
            val log = project.changelog
            val item = log.getOrNull(project.version.toString()) ?: log.getUnreleased()
            log.renderItem(item.withHeader(false).withEmptySections(false), Changelog.OutputType.HTML)
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}
