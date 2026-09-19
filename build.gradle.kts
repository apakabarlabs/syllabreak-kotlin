plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-library`
    `maven-publish`
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("org.jetbrains.dokka") version "2.2.0"
}

group = "fm.apakabar"
version = "0.20.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.heapy.kotaml:kotaml:0.111.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

java {
    withSourcesJar()
    withJavadocJar()
}

dokka {
    dokkaPublications.html {
        moduleName.set("Syllabreak for Kotlin")
        moduleVersion.set(project.version.toString())
        outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
        includes.from("docs/module.md")
    }
    dokkaSourceSets.configureEach {
        sourceRoots.from(file("src/main/kotlin"))
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl.set(uri("https://github.com/apakabarlabs/syllabreak-kotlin/tree/main/src/main/kotlin"))
            remoteLineSuffix.set("#L")
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

ktlint {
    version.set("1.2.1")
    android.set(false)
    outputToConsole.set(true)
    outputColorName.set("RED")
    ignoreFailures.set(false)
    filter {
        exclude("**/generated/**")
        include("**/kotlin/**")
    }
}
