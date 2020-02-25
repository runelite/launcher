
import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.apache.tools.ant.filters.ReplaceTokens

buildscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.github.ben-manes:gradle-versions-plugin:0.28.0")
    }
}

plugins {
    id("com.github.johnrengelman.shadow") version "5.2.0"
    id("com.github.ben-manes.versions") version "0.28.0"
    id("se.patrikerdes.use-latest-versions") version "0.2.13"

    java
    checkstyle
}

group = "com.openosrs"
version = "2.2.0"
description = "OpenOSRS Launcher"

repositories {
    mavenLocal()
    maven {
        url = uri("https://repo1.maven.org/maven2")
    }

    maven {
        url = uri("https://raw.githubusercontent.com/open-osrs/maven-repo/master")
    }
}

dependencies {
    annotationProcessor(group = "org.projectlombok", name = "lombok", version = "1.18.12")


    compileOnly(group = "org.projectlombok", name = "lombok", version = "1.18.12")

    implementation(group = "org.slf4j", name = "slf4j-api", version = "1.7.30")
    implementation(group = "ch.qos.logback", name = "logback-classic", version = "1.2.3")
    implementation(group = "net.sf.jopt-simple", name = "jopt-simple", version = "5.0.4")
    implementation(group = "com.google.code.gson", name = "gson", version = "2.8.6")
    implementation(group = "com.google.guava", name = "guava", version = "28.2-jre")
    implementation(group = "com.vdurmont", name = "semver4j", version = "3.1.0")

    testImplementation(group = "junit", name = "junit", version = "4.13")
}

configure<CheckstyleExtension> {
    maxWarnings = 0
    toolVersion = "8.25"
    isShowViolations = true
    isIgnoreFailures = false
}

fun isNonStable(version: String): Boolean {
    return listOf("ALPHA", "BETA", "RC").any {
        version.toUpperCase().contains(it)
    }
}

tasks {
    java {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    build {
        finalizedBy("shadowJar")
    }

    processResources {
        val tokens = mapOf(
                "basedir"         to project.projectDir.path,
                "finalName"       to "OpenOSRS",
                "artifact"        to "launcher",
                "project.version" to project.version,
                "project.group"   to project.group,
                "description"     to "OpenOSRS launcher"
        )

        copy {
            from("${rootDir}/packr") {
                include("Info.plist")
            }
            from("${rootDir}/innosetup") {
                include("openosrs.iss")
                include("openosrs32.iss")
            }
            from("${rootDir}/appimage") {
                include("openosrs.desktop")
            }
            into("${buildDir}/filtered-resources/")

            filter(ReplaceTokens::class, "tokens" to tokens)
            filteringCharset = "UTF-8"
        }

        doLast {
            copy {
                from("src/main/resources") {
                    include("launcher.properties")
                }
                into("${buildDir}/resources/main/net/runelite/launcher")

                filter(ReplaceTokens::class, "tokens" to tokens)
                filteringCharset = "UTF-8"
            }
        }
    }

    jar {
        manifest {
            attributes(mutableMapOf("Main-Class" to "net.runelite.launcher.Launcher"))
        }
    }

    shadowJar {
        archiveClassifier.set("shaded")
        exclude("net/runelite/injector/**")
    }

    named<DependencyUpdatesTask>("dependencyUpdates") {
        checkForGradleUpdate = false

        resolutionStrategy {
            componentSelection {
                all {
                    if (candidate.displayName.contains("fernflower") || isNonStable(candidate.version)) {
                        reject("Non stable")
                    }
                }
            }
        }
    }
}
