import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test

plugins { base }

allprojects {
    group = "ru.servermine"
    version = "0.1.0-SNAPSHOT"
    repositories {
        mavenCentral()
        maven("https://repo.purpurmc.org/snapshots")
    }
}

subprojects {
    if (path.count { it == ':' } >= 2) {
        apply(plugin = "java-library")
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(25))
            withSourcesJar()
        }
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.release.set(25)
        }
        dependencies {
            add("testImplementation", platform("org.junit:junit-bom:5.13.4"))
            add("testImplementation", "org.junit.jupiter:junit-jupiter")
            add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
        }
        tasks.withType<Test>().configureEach { useJUnitPlatform() }
        tasks.withType<ProcessResources>().configureEach { filteringCharset = "UTF-8" }
    }
}

tasks.register<Zip>("resourcePack") {
    from("resource-pack") { exclude("README.md") }
    archiveFileName.set("ServerMineCities-ResourcePack.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.assemble { dependsOn("resourcePack") }

val pluginVersion = version.toString()
tasks.register("verifyPackaging") {
    group = "verification"
    description = "Checks the standalone Cities plugin and its client resource pack."
    dependsOn(":cities:cities-plugin:jar", "resourcePack")
    doLast {
        fun entries(path: String): Set<String> = java.util.zip.ZipFile(layout.projectDirectory.file(path).asFile).use { zip ->
            zip.entries().asSequence().map { it.name }.toSet()
        }
        val jar = entries("cities/cities-plugin/build/libs/ServerMineCities-$pluginVersion.jar")
        val pack = entries("build/distributions/ServerMineCities-ResourcePack.zip")
        check("plugin.yml" in jar)
        check("ru/servermine/cities/plugin/ServerMineCitiesPlugin.class" in jar)
        check("ru/servermine/cities/api/CitiesService.class" in jar)
        check(jar.none { it.startsWith("ru/servermine/economy/") }) { "Cities must not bundle Economy classes" }
        check("pack.mcmeta" in pack)
        check("assets/gradostroy/font/gui.json" in pack)
        check("assets/gradostroy/font/book/line_27.json" in pack)
        check("assets/gradostroy/textures/font/book/text.png" in pack)
    }
}

tasks.check { dependsOn("verifyPackaging") }
