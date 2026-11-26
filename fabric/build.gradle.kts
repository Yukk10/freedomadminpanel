plugins {
    id("fabric-loom")
}

version = property("fabric_version") as String
base.archivesName.set("freedomadminpanel-fabric")

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}

// Номер протокола общий с плагином на Velocity (gradle.properties -> protocol_version)
val generateTemplates = tasks.register<Copy>("generateTemplates") {
    val props = mapOf(
        "version" to project.version.toString(),
        "protocol" to project.property("protocol_version").toString(),
    )
    inputs.properties(props)
    from("src/main/templates")
    into(layout.buildDirectory.dir("generated/sources/templates"))
    expand(props)
}
sourceSets.main { java.srcDir(generateTemplates.map { it.destinationDir }) }
