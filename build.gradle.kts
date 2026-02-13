import gg.meza.stonecraft.mod
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

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
}

tasks.withType<Jar>().configureEach {
    manifest.attributes["MixinConfigs"] = "nickhider.mixins.json"
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    if (name == "compileTestJava" && tasks.names.contains("generatePackMCMetaJson")) {
        dependsOn(tasks.named("generatePackMCMetaJson"))
    }
}
