import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.1.2"

stonecutter parameters {
    replacements {
        // 26.3: Blaze3D-Grafik-API heißt jetzt „renderpearl“ (als Import und in Mixin-Zielen)
        string(current.parsed >= "26.3") {
            replace("com.mojang.blaze3d.buffers.GpuBufferSlice", "com.mojang.renderpearl.api.buffers.GpuBufferSlice")
            replace("Lcom/mojang/blaze3d/buffers/GpuBufferSlice;", "Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;")
            // 26.3: EnderMan heißt Enderman
            replace("net.minecraft.world.entity.monster.EnderMan;", "net.minecraft.world.entity.monster.Enderman;")
            replace("@Mixin(EnderMan.class)", "@Mixin(Enderman.class)")
            replace("instanceof EnderMan ", "instanceof Enderman ")
        }
    }
}

// Version aus stonecutter.properties.toml; der GitHub-Build hängt seine Nummer an (-PbuildNumber=42 → 1.0.0-build.42)
val modVersion: String = Regex("""mod\.version\s*=\s*"([^"]+)"""")
    .find(file("stonecutter.properties.toml").readText())!!.groupValues[1] +
    (findProperty("buildNumber")?.let { "-build.$it" } ?: "")
extra["modVersion"] = modVersion

/**
 * Eine Datei für alle Versionen: Jede Versions-Jar steckt als Jar-in-Jar darin (alle mit derselben
 * Mod-ID „trycity_core“, je eine Minecraft-Version). Fabric lädt nur die, die zur laufenden
 * Version passt. Die äußere Mod „trycity“ verlangt nur, dass eine davon geladen wird.
 */
val bundleJar = tasks.register("bundleJar") {
    group = "build"
    description = "Packt alle Versions-Jars in eine Datei (build/libs/trycity-content-<version>.jar)"
    val versionJars = stonecutter.versions.map { node -> project(node.project).tasks.named("jar") }
    dependsOn(versionJars)
    // Ohne angemeldete Eingaben hielte Gradle die Datei nach Code-Änderungen für aktuell
    inputs.files(versionJars.map { jar -> jar.map { it.outputs.files } })
    inputs.property("version", modVersion)
    val output = layout.buildDirectory.file("libs/trycity-content-$modVersion.jar")
    outputs.file(output)
    doLast {
        val jars = versionJars.map { it.get().outputs.files.singleFile }
        val target = output.get().asFile
        target.parentFile.listFiles { f -> f.name.startsWith("trycity-content-") && f != target }?.forEach { it.delete() }
        ZipOutputStream(target.outputStream()).use { zip ->
            val nested = jars.joinToString(",\n    ") { "{ \"file\": \"META-INF/jars/${it.name}\" }" }
            val modJson = """
                {
                  "schemaVersion": 1,
                  "id": "trycity",
                  "version": "$modVersion",
                  "name": "TryCity Content",
                  "description": "Extras für TryCity: Inhalte, die der Server dem Client per Paket ankündigt. Eine Datei für Minecraft 26.1.2, 26.2 und 26.3.",
                  "authors": ["leonm"],
                  "license": "All-Rights-Reserved",
                  "environment": "client",
                  "jars": [
                    $nested
                  ],
                  "depends": {
                    "trycity_core": "*"
                  }
                }
            """.trimIndent()
            zip.putNextEntry(ZipEntry("fabric.mod.json"))
            zip.write(modJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for (jar in jars) {
                zip.putNextEntry(ZipEntry("META-INF/jars/${jar.name}"))
                jar.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        logger.lifecycle("Alle Versionen in einer Datei: ${target.name} (${jars.joinToString { it.name }})")
    }
}

// Nach jedem Build direkt in den NoRisk-Client (Profil „new“). Ältere trycity-content-*.jar
// fliegen vorher raus, sonst lägen zwei Stände nebeneinander. Läuft Minecraft noch, sperrt
// Windows die alte Datei – dann bricht der Build nicht ab, sondern sagt Bescheid.
// Ohne NoRisk (GitHub-Build) passiert nichts.
val noriskMods = file("${System.getenv("APPDATA")}/norisk/NoRiskClientV3/data/profiles/noriskclient/new/mods")

val installNoRisk = tasks.register("installNoRisk") {
    group = "build"
    description = "Kopiert die fertige Mod in den NoRisk-Mods-Ordner"
    dependsOn(bundleJar)
    onlyIf { System.getenv("APPDATA") != null && noriskMods.isDirectory }
    doLast {
        val jarFile = bundleJar.get().outputs.files.singleFile
        try {
            project.delete(fileTree(noriskMods) { include("trycity-content-*.jar") })
            project.copy {
                from(jarFile)
                into(noriskMods)
            }
            logger.lifecycle("TryCity Content -> NoRisk: ${jarFile.name}")
        } catch (e: Exception) {
            logger.warn("NoRisk-Mods-Ordner gesperrt – läuft Minecraft noch? Spiel schließen und nochmal bauen. " +
                    "Die neue Mod liegt in $jarFile")
        }
    }
}

// ./gradlew build baut alle Versionen und die gebündelte Datei
tasks.register("build") {
    group = "build"
    dependsOn(bundleJar)
    finalizedBy(installNoRisk)
}
