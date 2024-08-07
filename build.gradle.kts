@file:Suppress("VulnerableLibrariesLocal")

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path


repositories {
    maven("https://repo.maven.apache.org/maven2")
    maven("https://repo.runelite.net")
}

plugins {
    alias(libs.plugins.shadowjar)
}

dependencies {
    implementation("org.slf4j:slf4j-api:1.7.25")
    implementation("ch.qos.logback:logback-classic:1.2.9")
    implementation("net.sf.jopt-simple:jopt-simple:5.0.1")
    implementation("com.google.code.gson:gson:2.8.5")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("com.google.guava:guava:23.2-jre") {
        exclude("com.google.code.findbugs", "jsr305")
        exclude("com.google.errorprone", "error_prone_annotations")
        exclude("com.google.j2objc", "j2objc-annotations")
        exclude("org.codehaus.mojo", "animal-sniffer-annotations")
    }
    implementation("org.projectlombok:lombok:1.18.20")
    annotationProcessor("org.projectlombok:lombok:1.18.20")
    implementation("net.runelite.archive-patcher:archive-patcher-applier:1.2")
    testImplementation("junit:junit:4.12")
    implementation(projects.patch)
    implementation(projects.patch.patchRunelite)
    implementation(libs.junixsocket)
    implementation(platform(libs.log4j.bom))
    implementation(libs.bundles.log4j)
}

private val runelitePath: Path = Path(System.getProperty("user.home"), ".rsprox", "runelite")
Files.createDirectories(runelitePath)
val shadowJar =
    tasks.withType<ShadowJar> {
        archiveBaseName.set("runelite-launcher")
        archiveClassifier.set("")
        archiveVersion.set("")
        this.destinationDirectory.set(runelitePath.toFile())
        this.isZip64 = true
        mergeServiceFiles()
    }

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "net.runelite.launcher.Launcher"
    }
}

tasks.build {
    finalizedBy(shadowJar)
}
