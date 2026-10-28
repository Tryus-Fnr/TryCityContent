plugins {
    id("net.fabricmc.fabric-loom")
}

// NoRisk und .minecraft liegen unter %APPDATA% – kein fest verdrahteter Benutzerordner.
val appData: String = System.getenv("APPDATA").orEmpty()

// Die Versions-Jar heißt „trycity-content-core-1.0.0+mc26.2.jar“; ausgeliefert wird nur :bundleJar
version = "${rootProject.extra["modVersion"]}+mc${sc.current.version}"
group = "de.leon"
base.archivesName = "trycity-content-core"

repositories {
    // Sodium/Iris für den Selbsttest mit Shaderpack
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    implementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}")

    // Nur zum Testen mit Shaderpack: ./gradlew :26.2:runClientGameTest -PwithIris
    // (Sodium + Iris von Modrinth, kommen nicht in die Mod).
    if (project.hasProperty("withIris")) {
        "localRuntime"("maven.modrinth:sodium:${sc.properties.get<String>("deps.sodium")}")
        "localRuntime"("maven.modrinth:iris:${sc.properties.get<String>("deps.iris")}")
    }
}

// Selbsttest mit Iris-Shaderpack – gibt es nur mit -PwithIris, ein normaler Build
// startet nie einen Client:   ./gradlew :26.1.2:runClientGameTest -PwithIris
// Legt eine Welt an, schaltet das Tryus-Pack ein, stellt Claim-Marker und ein
// leuchtendes Schwein hin und macht Bilder ohne und mit Shaderpack
// (versions/<v>/build/run/clientGameTest/screenshots). Prüft beim Start alle Mixins.
if (project.hasProperty("withIris")) {
    fabricApi {
        configureTests {
            createSourceSet = true
            modId = "trycity-test"
            enableGameTests = false
            enableClientGameTests = true
            eula = true
            clearRunDirectory = false
        }
    }
    val testRun = layout.buildDirectory.dir("run/clientGameTest").get().asFile
    val prepareIrisTest = tasks.register("prepareIrisTest") {
        doLast {
            // Kopie, kein Link: ein Aufräumen des Testordners darf nie das echte Pack treffen.
            project.sync {
                from("$appData/.minecraft/resourcepacks/Tryus")
                into(File(testRun, "resourcepacks/Tryus"))
                exclude(".git/**", "tools/**")
            }
            // Complementary aus dem NoRisk-Profil (oder .minecraft) – das Pack, mit dem gespielt wird
            val shaderPack = listOf(
                "$appData/norisk/NoRiskClientV3/data/profiles/noriskclient/new/shaderpacks",
                "$appData/.minecraft/shaderpacks",
            ).flatMap { dir -> File(dir).listFiles { f -> f.name.startsWith("ComplementaryReimagined") && f.name.endsWith(".zip") }.orEmpty().toList() }
                .maxByOrNull { it.name } ?: throw GradleException("Kein ComplementaryReimagined*.zip im NoRisk-Profil oder in .minecraft/shaderpacks")
            project.copy {
                from(shaderPack)
                into(File(testRun, "shaderpacks"))
            }
            val iris = File(testRun, "config/iris.properties")
            iris.parentFile.mkdirs()
            iris.writeText("shaderPack=${shaderPack.name}\nenableShaders=false\n")
        }
    }
    tasks.matching { it.name == "runClientGameTest" }.configureEach { dependsOn(prepareIrisTest) }
}

loom {
    runConfigs.all {
        // prüft beim Start alle Mixin-Ziele
        vmArgs("-Dtrycity.audit=true")
        // Test der fertigen Datei für alle Versionen: Mod nur aus der gebündelten Jar laden
        // (./gradlew bundleJar, dann :<v>:runClientGameTest -PwithIris -Pbundletest)
        if (project.hasProperty("bundletest")) {
            vmArgs("-Dfabric.addMods=" + rootProject.file("build/libs").absolutePath)
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks {
    withType<JavaCompile>().configureEach {
        options.release = 25
        options.encoding = "UTF-8"
    }

    processResources {
        val props = mapOf(
            "version" to project.version.toString(),
            "minecraft" to sc.properties.get<String>("mod.mc_compat"),
        )
        props.forEach { (key, value) -> inputs.property(key, value) }
        filesMatching("fabric.mod.json") { expand(props) }
        // Beim Test der gebündelten Datei: die Dev-Klassen sind dann keine eigene Mod
        val bundleTest = project.hasProperty("bundletest")
        inputs.property("bundletest", bundleTest)
        if (bundleTest) exclude("fabric.mod.json")
    }
}
