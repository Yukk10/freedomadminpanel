plugins {
    java
    id("com.gradleup.shadow")
}

version = property("velocity_version") as String
base.archivesName.set("freedomadminpanel-velocity")

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.org/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:${property("velocity_api_version")}")
    annotationProcessor("com.velocitypowered:velocity-api:${property("velocity_api_version")}")
    compileOnly("com.gitlab.ruany:LiteBansAPI:${property("litebans_api_version")}")
    compileOnly("net.skinsrestorer:skinsrestorer-api:${property("skinsrestorer_api_version")}")
    implementation("org.mariadb.jdbc:mariadb-java-client:${property("mariadb_version")}")
}

// Версия и номер протокола попадают в код через BuildConstants (нужны в аннотации @Plugin)
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

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("org.mariadb.jdbc", "dev.freedomadminpanel.velocity.libs.mariadb")
    mergeServiceFiles()
    exclude("META-INF/LICENSE*", "META-INF/NOTICE*")
}
tasks.jar { archiveClassifier.set("plain") }
tasks.build { dependsOn(tasks.shadowJar) }
