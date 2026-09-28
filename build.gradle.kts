import com.vanniktech.maven.publish.DeploymentValidation
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.SourcesJar

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-library`
    id("com.vanniktech.maven.publish") version "0.37.0"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("org.jetbrains.dokka") version "2.2.0"
}

group = "fm.apakabar"
version =
    requireNotNull(
        Regex("""^## (\d+\.\d+\.\d+)$""", RegexOption.MULTILINE)
            .find(file("CHANGELOG.md").readText()),
    ) { "CHANGELOG.md has no released version heading" }.groupValues[1]

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

mavenPublishing {
    configure(
        KotlinJvm(
            javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
            sourcesJar = SourcesJar.Sources(),
        ),
    )
    publishToMavenCentral(automaticRelease = true, validateDeployment = DeploymentValidation.PUBLISHED)
    if (!providers.gradleProperty("unsignedLocalPublish").isPresent) {
        signAllPublications()
    }
    coordinates("fm.apakabar", "syllabreak-kotlin", version.toString())
    pom {
        name.set("Syllabreak for Kotlin")
        description.set("Syllable breaking and hyphenation across languages, with language detection.")
        url.set("https://github.com/apakabarlabs/syllabreak-kotlin")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("apakabarlabs")
                name.set("Apakabar")
                url.set("https://github.com/apakabarlabs")
            }
        }
        scm {
            url.set("https://github.com/apakabarlabs/syllabreak-kotlin")
            connection.set("scm:git:https://github.com/apakabarlabs/syllabreak-kotlin.git")
            developerConnection.set("scm:git:ssh://git@github.com/apakabarlabs/syllabreak-kotlin.git")
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
