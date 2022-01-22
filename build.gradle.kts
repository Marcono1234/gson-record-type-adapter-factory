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

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    // Publish sources and javadoc
    withSourcesJar()
    withJavadocJar()
}

val gsonVersion = "2.8.9"
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
