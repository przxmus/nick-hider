pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev")
        maven("https://maven.minecraftforge.net")
        maven("https://maven.neoforged.net/releases/")
    }
}
plugins {
    id("gg.meza.stonecraft") version "1.9.0"
    id("dev.kikugie.stonecutter") version "0.8.3"
}

stonecutter {
    centralScript = "build.gradle.kts"
    kotlinController = true
    shared {
        fun mc(version: String, vararg loaders: String) {
            // Make the relevant version directories named "1.20.1-fabric", "1.20.1-forge", etc.
            for (it in loaders) version("$version-$it", version)
        }

        mc("1.20.1", "fabric", "forge")
        mc("1.21.1", "fabric", "forge", "neoforge")

        vcsVersion = "1.20.1-forge"
    }
    create(rootProject)
}

rootProject.name = "Nick Hider"
