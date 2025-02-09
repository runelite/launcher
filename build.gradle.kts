/*
 * Copyright (c) 2025, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import aws.sdk.kotlin.runtime.auth.credentials.EnvironmentCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.smithy.kotlin.runtime.content.asByteStream
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.daemon.common.toHexString
import java.security.MessageDigest
import kotlin.io.path.fileSize

plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.runelite.net") }
    maven { url = uri("https://cdn.rsprox.net/maven") }
}

group = "net.runelite"
version = "2.7.4-SNAPSHOT"
description = "RuneLite Launcher"

dependencies {
    implementation(libs.org.slf4j.slf4j.api)
    implementation(libs.ch.qos.logback.logback.classic)
    implementation(libs.net.sf.jopt.simple.jopt.simple)
    implementation(libs.com.google.code.gson.gson)
    implementation(libs.com.google.guava.guava) {
        // compile time annotations for static analysis in Guava
        // https://github.com/google/guava/wiki/UseGuavaInYourBuild#what-about-guavas-own-dependencies
        exclude(group = "com.google.code.findbugs", module = "jsr305")
        exclude(group = "com.google.errorprone", module = "error_prone_annotations")
        exclude(group = "com.google.j2objc", module = "j2objc-annotations")
        exclude(group = "org.codehaus.mojo", module = "animal-sniffer-annotations")
    }
    implementation(libs.net.runelite.archive.patcher.archive.patcher.applier)
    compileOnly(libs.com.google.code.findbugs.jsr305)
    compileOnly(libs.org.projectlombok.lombok)
    annotationProcessor(libs.org.projectlombok.lombok)
    testImplementation(libs.junit.junit)

    // rsprox
    implementation(libs.bundles.rsprox)
}

buildscript {
    dependencies {
        classpath(libs.com.google.code.gson.gson)
        classpath(libs.aws.sdk.kotlin.s3)
        classpath(libs.jaxb.api) // s3 maven-publish dependency
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(11)
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

sourceSets.create("java8") {
    java.srcDirs("src/main/java8")
}

tasks.jar {
    from(sourceSets["java8"].output)
    duplicatesStrategy = DuplicatesStrategy.WARN
}

tasks.getByName<JavaCompile>("compileJava8Java") {
    options.release.unset()
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}

tasks {
    processResources {
        filesMatching("**/*.properties") {
            val props = if (project.findProperty("RUNELITE_BUILD") as? String == "runelite")
                arrayOf(
                    "runelite_net" to "runelite.net",
                    "runelite_128" to "runelite_128.png",
                    "runelite_splash" to "runelite_splash.png"
                )
            else arrayOf(
                "runelite_net" to "",
                "runelite_128" to "",
                "runelite_splash" to ""
            )
            expand(
                "project" to project,
                *props
            )
        }
    }
}

tasks.register<Copy>("filterAppimage") {
    from("appimage/runelite.desktop")
    into("build/filtered-resources")
    expand("project" to project)
}

tasks.register<Copy>("filterInnosetup") {
    from("innosetup") {
        include("*.iss")
    }
    into("build/filtered-resources")
    expand("project" to project) {
        escapeBackslash = true
    }
}

tasks.register<Copy>("copyInstallerScripts") {
    from("innosetup") {
        include("*.pas")
    }
    // not really filtered, but need to be put next to the filtered installer scripts so they can pick them up
    into("build/filtered-resources")
}

tasks.register<Copy>("filterOsx") {
    from("osx/Info.plist")
    into("build/filtered-resources")
    expand("project" to project)
}

tasks.shadowJar {
    from(sourceSets.main.get().output)
    from(sourceSets.getByName("java8").output)
    minimize {
        exclude(dependency("ch.qos.logback:.*:.*"))
    }
    archiveFileName.set(project.findProperty("finalName") as String + ".jar")
    manifest {
        attributes("Main-Class" to "net.runelite.launcher.Launcher")
    }
}

tasks.named("build") {
    dependsOn("filterAppimage", "filterInnosetup", "copyInstallerScripts", "filterOsx", tasks.shadowJar)
}

data class Bootstrap(
    val launcher: Launcher,
    val artifacts: List<Artifact>,
)

data class Launcher(
    val version: String,
    val mainClass: String,
)

data class Artifact(
    val name: String,
    val path: String,
    val size: Long,
    val hash: String,
)

fun sha256Hash(bytes: ByteArray): String {
    val messageDigest = MessageDigest.getInstance("SHA-256")
    messageDigest.update(bytes)
    return messageDigest.digest().toHexString()
}

suspend fun uploadToS3(
    s3Client: S3Client,
    file: File,
    s3Path: String,
): Artifact {
    val request =
        PutObjectRequest {
            bucket = "cdn.rsprox.net"
            key = s3Path
            body = file.asByteStream()
        }
    s3Client.putObject(request)
    println("Uploaded $file to s3://cdn.rsprox.net/$s3Path")
    return Artifact(
        file.name,
        "https://cdn.rsprox.net/$s3Path",
        file.toPath().fileSize(),
        sha256Hash(file.readBytes()),
    )
}

tasks.register("uploadJarsToS3") {
    doLast {
        val outputFile = file("bootstrap.json")
        val artifacts = mutableListOf<Artifact>()

        val projectArtifacts =
            configurations.runtimeClasspath
                .get()
                .resolvedConfiguration
                .resolvedArtifacts

        runBlocking {
            S3Client
                .fromEnvironment {
                    region = "eu-west-1"
                    credentialsProvider = EnvironmentCredentialsProvider()
                }.use { s3 ->
                    val launcherJar = File("build/libs/launcher-${version}.jar")
                    artifacts += uploadToS3(s3, launcherJar, "runelite/launcher/net/runelite/launcher/$version/${launcherJar.name}")

                    for (artifact in projectArtifacts) {
                        if (artifact.type != "jar") continue
                        val group =
                            artifact.moduleVersion.id.group
                                .replace('.', '/')
                        val version = artifact.moduleVersion.id.version
                        val jarFile = artifact.file

                        val prefix = "runelite/launcher/$group/${artifact.name}/$version/"

                        artifacts += uploadToS3(s3, jarFile, "$prefix${jarFile.name}")
                    }
                }
        }

        val bootstrap =
            Bootstrap(
                launcher = Launcher(version = version.toString(), mainClass = "net.runelite.launcher.Launcher"),
                artifacts = artifacts.sortedBy { it.name },
            )

        println("Uploaded ${artifacts.size} artifacts to S3")
        outputFile.writeText(Gson().toJson(bootstrap))
    }
}
