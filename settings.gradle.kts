pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "freedomadminpanel"

include("velocity", "paper", "fabric", "stubs:cmi-api")
