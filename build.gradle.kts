import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

plugins {
    `java-library`
    `maven-publish`
    id("pl.allegro.tech.build.axion-release") version "1.13.3"
}

repositories {
    mavenCentral()
}

group = "marcono1234.gson"
// See https://axion-release-plugin.readthedocs.io/en/latest/
version = scmVersion.version

typealias HooksConfig = pl.allegro.tech.build.axion.release.domain.hooks.HooksConfig
typealias HookContext = pl.allegro.tech.build.axion.release.domain.hooks.HookContext
scmVersion {
    // Plugin does not support Kotlin DSL yet, see https://github.com/allegro/axion-release-plugin/issues/285
    hooks(closureOf<HooksConfig> {
        // See pl.allegro.tech.build.axion.release.domain.hooks.FileUpdateHookAction
        pre("fileUpdate", mapOf<Any, Any>(
            "file" to "CHANGELOG.md",
            // Use positive lookbehind to insert release header behind it
            "pattern" to KotlinClosure2<String, HookContext, String>(
                {previousVersion, hookContext -> "(?<=${Regex.escape("## [Unreleased ???] - ???")})"}
            ),
            "replacement" to KotlinClosure2<String, HookContext, String>(
                {releaseVersion, hookContext -> "\n\n## [$releaseVersion] - ${DateTimeFormatter.ISO_LOCAL_DATE.format(LocalDate.now(ZoneOffset.UTC))}"}
            )
        ))
        // Commit CHANGELOG file with default release message
        pre("commit")
    })
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    // Publish sources and javadoc
    withSourcesJar()
    withJavadocJar()
}

val gsonVersion = "2.9.1"
dependencies {
    api("com.google.code.gson:gson:$gsonVersion")
}

val junitVersion = "5.8.2"
testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter(junitVersion)
        }

        // Separate test suite for blackbox module testing
        val testModular by registering(JvmTestSuite::class) {
            useJUnitJupiter(junitVersion)
            dependencies {
                implementation(project)

                // Manually declare dependency as workaround for https://github.com/gradle/gradle/issues/18627
                implementation("org.apiguardian:apiguardian-api:1.1.2")
            }
        }
    }
}

tasks.check {
    dependsOn(testing.suites.named("testModular"))
}

tasks.javadoc {
    options {
        // Workaround to use Standard Doclet options, see https://github.com/gradle/gradle/issues/7038#issuecomment-448294937
        this as StandardJavadocDocletOptions
        links = listOf("https://javadoc.io/doc/com.google.code.gson/gson/$gsonVersion/")
    }

    shouldRunAfter(tasks.check)
}

// Make build reproducible
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
