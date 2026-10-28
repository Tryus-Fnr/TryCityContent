pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        // 26.x ist unobfuskiert: das Loom ohne Remapping
        id("net.fabricmc.fabric-loom") version "1.18.4"
    }
}

plugins {
    // Mehrere Minecraft-Versionen aus einem Quelltext (https://stonecutter.kikugie.dev)
    id("dev.kikugie.stonecutter") version "0.9.8"
}

stonecutter {
    create(rootProject) {
        // Je Version eine eigene Jar; am Ende packt :bundleJar alle in eine Datei
        versions("26.1.2", "26.2", "26.3")
        // So steht der Quelltext im Ordner (aktive Version beim Bearbeiten)
        vcsVersion = "26.1.2"
    }
}

rootProject.name = "trycity-content"
