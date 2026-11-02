pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
            content { includeGroupByRegex("net\\.fabricmc.*") }
        }
        gradlePluginPortal()
    }
}

plugins {
    // Lets Gradle download a Java 25 toolchain on machines that do not have one installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "veyrath"

// Platform-independent world engine (pure Java, no Minecraft dependency).
include("engine")
// Headless preview renderer, gallery generator and benchmark harness for the engine.
include("preview")

// The Fabric mod needs the Minecraft and Fabric maven servers. Builds on machines that
// cannot reach them (e.g. restricted CI) pass -Pveyrath.skipMod=true.
if (providers.gradleProperty("veyrath.skipMod").orNull != "true") {
    include("mod-fabric")
}
