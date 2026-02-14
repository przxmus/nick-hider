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
    id("gg.meza.stonecraft") version "1.9.+"
    id("dev.kikugie.stonecutter") version "0.8.+"
}

stonecutter {
    centralScript = "build.gradle.kts"
    kotlinController = true
    shared {
        fun mc(version: String, vararg loaders: String) {
            // Make the relevant version directories named "1.20.2-fabric", "1.20.2-forge", etc.
            for (it in loaders) version("$version-$it", version)
        }

        mc("1.20", "fabric", "forge")
        mc("1.20.1", "fabric", "forge")
        mc("1.20.2", "fabric", "forge", "neoforge")
        mc("1.20.3", "fabric")
        mc("1.20.4", "fabric", "forge", "neoforge")
        mc("1.20.5", "fabric")
        mc("1.20.6", "fabric", "forge", "neoforge")
        mc("1.21", "fabric", "neoforge")
        mc("1.21.1", "fabric", "neoforge")
        mc("1.21.2", "fabric")
        mc("1.21.3", "fabric", "neoforge")
        mc("1.21.4", "fabric", "neoforge")
        mc("1.21.5", "fabric", "neoforge")
        mc("1.21.6", "fabric")
        mc("1.21.7", "fabric")
        mc("1.21.8", "fabric", "neoforge")
        mc("1.21.9", "fabric")
        mc("1.21.10", "fabric", "neoforge")
        mc("1.21.11", "fabric")

        vcsVersion = "1.20.1-forge"
    }
    create(rootProject)
}

rootProject.name = "Nick Hider"
