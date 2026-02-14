import gg.meza.stonecraft.mod
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.language.jvm.tasks.ProcessResources

val matrixTestsEnabled = providers.gradleProperty("matrixTests")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)

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
    runDirectory = rootProject.layout.projectDirectory.dir("run/${mod.minecraftVersion}-${mod.loader}")

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

configurations.configureEach {
    if (mod.isForge || mod.loader == "neoforge") {
        exclude(group = "net.fabricmc", module = "fabric-log4j-util")
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
    if (matrixTestsEnabled.get()) {
        useJUnitPlatform()
        val targetJava = if (requiresJava21ForMinecraft(mod.minecraftVersion)) 21 else 17
        javaLauncher.set(javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(targetJava))
        })
    } else {
        enabled = false
    }
}

if (!matrixTestsEnabled.get()) {
    val disabledMatrixTestTasks = setOf(
        "testClasses",
        "compileTestJava",
        "processTestResources",
        "stonecutterPrepareTest",
        "stonecutterGenerateTest"
    )

    tasks.configureEach {
        if (name in disabledMatrixTestTasks) {
            enabled = false
        }
    }
}

tasks.withType<Jar>().configureEach {
    manifest.attributes["MixinConfigs"] = "nickhider.mixins.json"
    if (mod.isForge && mod.minecraftVersion == "1.20.3") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
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

tasks.named<ProcessResources>("processResources").configure {
    doLast {
        if (mod.isForge && mod.minecraftVersion == "1.20.3") {
            val resourcesMetaInfDir = destinationDir.resolve("META-INF")
            val modsToml = resourcesMetaInfDir.resolve("mods.toml")
            if (modsToml.exists()) {
                val classesRootDir = project.layout.buildDirectory.dir("classes/java/main").get().asFile
                val classesMetaInfDir = project.layout.buildDirectory.dir("classes/java/main/META-INF").get().asFile
                classesMetaInfDir.mkdirs()
                modsToml.copyTo(classesMetaInfDir.resolve("mods.toml"), overwrite = true)
                modsToml.delete()

                val mixinConfig = destinationDir.resolve("nickhider.mixins.json")
                if (mixinConfig.exists()) {
                    mixinConfig.copyTo(classesRootDir.resolve("nickhider.mixins.json"), overwrite = true)
                }

                val manifest = resourcesMetaInfDir.resolve("MANIFEST.MF")
                if (manifest.exists()) {
                    manifest.copyTo(classesMetaInfDir.resolve("MANIFEST.MF"), overwrite = true)
                }

                val packMetadata = destinationDir.resolve("pack.mcmeta")
                if (packMetadata.exists()) {
                    packMetadata.copyTo(classesRootDir.resolve("pack.mcmeta"), overwrite = true)
                }

                val assetsDir = destinationDir.resolve("assets")
                if (assetsDir.exists()) {
                    assetsDir.copyRecursively(classesRootDir.resolve("assets"), overwrite = true)
                }
            }
        }

        if (mod.loader == "neoforge" && (mod.minecraftVersion == "1.20.2" || mod.minecraftVersion == "1.20.4")) {
            val metaInfDir = destinationDir.resolve("META-INF")
            val neoForgeDescriptor = metaInfDir.resolve("neoforge.mods.toml")
            if (neoForgeDescriptor.exists()) {
                val source = neoForgeDescriptor.readText()
                val header = Regex("(?s)^(.*?description\\s*=\\s*'''[\\s\\S]*?''')")
                    .find(source)
                    ?.groupValues
                    ?.get(1)
                    ?: source
                val sanitized = header
                    .replace(Regex("(?m)^logoFile\\s*=.*\\n"), "")
                    .trimEnd() + "\n"
                metaInfDir.resolve("mods.toml").writeText(sanitized)
            }

            val packFormat = if (mod.minecraftVersion == "1.20.4") 22 else 18
            destinationDir.resolve("pack.mcmeta").writeText(
                """
                {
                  "pack": {
                    "pack_format": $packFormat,
                    "description": "Nick Hider"
                  }
                }
                """.trimIndent() + "\n"
            )
        }
    }
}
