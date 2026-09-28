plugins {
    id("fabric-loom") version "1.14-SNAPSHOT"
}

base {
    archivesName = providers.gradleProperty("archives_base_name").get()
    version = providers.gradleProperty("mod_version").get()
    group = providers.gradleProperty("maven_group").get()
}

repositories {
    maven("https://maven.meteordev.org/releases")
    maven("https://maven.meteordev.org/snapshots")
}

dependencies {
    minecraft("com.mojang:minecraft:1.21.11")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:0.18.4")
    modImplementation("meteordevelopment:meteor-client:1.21.11-SNAPSHOT")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version,
        "minecraft_version" to ">=1.21.11 <1.22",
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") {
        expand(props)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}
