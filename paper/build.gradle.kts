plugins {
    java
    id("com.gradleup.shadow")
}

version = property("paper_version") as String
base.archivesName.set("freedomadminpanel-paper")

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${property("paper_api_version")}")
    // CMI платный и не публикует API в maven — компилируемся против заглушек (в jar они не попадают)
    compileOnly(project(":stubs:cmi-api"))
    implementation("org.mariadb.jdbc:mariadb-java-client:${property("mariadb_version")}")
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("org.mariadb.jdbc", "dev.freedomadminpanel.paper.libs.mariadb")
    mergeServiceFiles()
    exclude("META-INF/LICENSE*", "META-INF/NOTICE*")
}
tasks.jar { archiveClassifier.set("plain") }
tasks.build { dependsOn(tasks.shadowJar) }
