plugins {
    `java-library`
    `maven-publish`
}

repositories {
    mavenCentral()
}

group = "marcono1234.gson"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    // Publish sources and javadoc
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    api("com.google.code.gson:gson:2.8.8")

    testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
    // Manually declare dependency as workaround for https://github.com/junit-team/junit5/issues/2730
    testImplementation("org.apiguardian:apiguardian-api:1.1.2")
}

// Separate source set for blackbox module testing
// Based on https://docs.gradle.org/current/userguide/java_testing.html#sec:configuring_java_integration_tests
// TODO: Maybe move to separate subproject? See https://docs.gradle.org/current/userguide/java_testing.html#blackbox_integration_testing
val testModularSourceSetName = "test-modular"
sourceSets {
    create(testModularSourceSetName) {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

val testModularImplementation by configurations.getting {
    extendsFrom(configurations.implementation.get())
    extendsFrom(configurations.testImplementation.get())
}

val testModularRuntimeOnly by configurations.getting {
    extendsFrom(configurations.runtimeOnly.get())
}

val testModular = task<Test>("testModular") {
    description = "Runs blackbox module tests."
    group = "verification"

    testClassesDirs = sourceSets[testModularSourceSetName].output.classesDirs
    classpath = sourceSets[testModularSourceSetName].runtimeClasspath
    useJUnitPlatform()
}

tasks.check {
    dependsOn(testModular)
}

tasks.test {
    useJUnitPlatform()
}

tasks.javadoc {
    options {
        // Workaround to use Standard Doclet options, see https://github.com/gradle/gradle/issues/7038#issuecomment-448294937
        this as StandardJavadocDocletOptions // unsafe cast
        links = listOf("https://javadoc.io/doc/com.google.code.gson/gson/latest/")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
