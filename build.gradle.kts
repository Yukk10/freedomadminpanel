plugins {
    id("com.gradleup.shadow") version "9.6.1" apply false
    id("fabric-loom") version "1.13.6" apply false
}

subprojects {
    group = "dev.freedomadminpanel"

    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.release.set(21)
        }
        tasks.withType<ProcessResources>().configureEach {
            filteringCharset = "UTF-8"
        }
    }
}

// Собирает все три jar в build/release/ (это же кладёт CI в релиз)
val collectJars = tasks.register<Copy>("collectJars") {
    group = "build"
    description = "Собирает jar для Velocity, Paper и Fabric в build/release"
    from(project(":velocity").tasks.named("shadowJar"))
    from(project(":paper").tasks.named("shadowJar"))
    from(project(":fabric").tasks.named("remapJar"))
    into(layout.buildDirectory.dir("release"))
}

tasks.register("buildAll") {
    group = "build"
    description = "Собрать все части проекта"
    dependsOn(collectJars)
}
