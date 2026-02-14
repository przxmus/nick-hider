import gg.meza.stonecraft.mod
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion

fun requiresJava21ForMinecraft(version: String): Boolean {
    val parts = version.split('.')
    val major = parts.getOrNull(0)?.toIntOrNull() ?: return false
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: return false
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0

    if (major != 1) {
        return false
    }

    return minor > 20 || (minor == 20 && patch >= 5)
}

plugins {
    id("gg.meza.stonecraft")
}

modSettings {
    clientOptions {
        fov = 90
        guiScale = 3
        narrator = false
        darkBackground = true
        musicVolume = 0.0
    }
}

repositories {
    maven {
        url = uri(rootProject.layout.projectDirectory.dir(".local-maven"))
    }
}


// Example of overriding publishing settings
publishMods {
    modrinth {
        if (mod.isFabric) requires("fabric-api")
    }

    curseforge {
        clientRequired = true // Set as needed
        serverRequired = false // Set as needed
        if (mod.isFabric) requires("fabric-api")
    }
}

loom {
    runs {
        configureEach {
            property("mixin.config", "nickhider.mixins.json")
        }
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    val targetJava = if (requiresJava21ForMinecraft(mod.minecraftVersion)) 21 else 17
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(targetJava))
    })
}

tasks.withType<Jar>().configureEach {
    manifest.attributes["MixinConfigs"] = "nickhider.mixins.json"
}

tasks.withType<JavaCompile>().configureEach {
    val targetJava = if (requiresJava21ForMinecraft(mod.minecraftVersion)) 21 else 17
    javaCompiler.set(javaToolchains.compilerFor {
        languageVersion.set(JavaLanguageVersion.of(targetJava))
    })
    options.release.set(targetJava)
    if (name == "compileTestJava" && tasks.names.contains("generatePackMCMetaJson")) {
        dependsOn(tasks.named("generatePackMCMetaJson"))
    }
}
