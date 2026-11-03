// VEYRATH Fabric mod: thin adapter between the platform-independent engine and Minecraft 26.3.
//
// NOTE: this module has not been compiled yet. The cloud build machine cannot reach the
// Fabric/Mojang/Modrinth maven servers; the first compile happens on the development PC
// (see docs/TESTING_GUIDE.md, "First device build").
//
// Build rules for unobfuscated Minecraft (26.1+), from Fabric's 26.1 release post and porting
// guide: plugin id net.fabricmc.fabric-loom, no mappings line, plain implementation/compileOnly
// instead of modImplementation/modCompileOnly, jar instead of remapJar.
//
// Flags:
//   -Pveyrath.dh=false       build without the Distant Horizons integration (dh/ package)
//   -Pveyrath.devMods=true   add Sodium, Iris and Distant Horizons to the development run client
plugins {
    alias(libs.plugins.fabric.loom)
}

description = "VEYRATH Fabric mod (Minecraft ${libs.versions.minecraft.get()})"

val withDh = providers.gradleProperty("veyrath.dh").getOrElse("true") != "false"
val devMods = providers.gradleProperty("veyrath.devMods").getOrElse("false") == "true"
// -Pveyrath.fullPack=true: the dev client loads every mod jar the installer put in the VEYRATH
// profile (modpack/modpack.json, same files and versions), except VEYRATH itself and Fabric API,
// which come from this build. Folder override: -Pveyrath.packMods=<path to mods folder>.
val fullPack = providers.gradleProperty("veyrath.fullPack").getOrElse("false") == "true"
val packMods = providers.gradleProperty("veyrath.packMods")
    .getOrElse(System.getenv("APPDATA") + "/.minecraft/instances/veyrath/mods")

base {
    archivesName.set("veyrath")
}

repositories {
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
}

loom {
    splitEnvironmentSourceSets()
    mods {
        create("veyrath") {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["client"])
        }
    }
    runs {
        named("client") {
            // Keep development runs out of the user's real game directory.
            runDir("run/client")
            vmArgs("-Xmx8G")
        }
        named("server") {
            runDir("run/server")
            vmArgs("-Xmx8G")
        }
    }
}

fabricApi {
    configureDataGeneration {
        client = true
    }
    // Client game test (seed tour, src/gametest): its own source set and mod, never in the mod jar.
    // Runs singleplayer only, so no server EULA is needed or accepted.
    configureTests {
        createSourceSet = true
        modId = "veyrath-gametest"
        enableGameTests = false
        enableClientGameTests = true
        eula = false
        // Keep run/clientGameTest between runs so a shader pack and iris.properties placed there
        // (shader tour) survive; the test always creates fresh worlds.
        clearRunDirectory = false
    }
}

// Seed tour options: -Pveyrath.tourSeeds=1,2,3; the screenshot setup tag follows -Pveyrath.devMods.
loom.runs.matching { it.name == "clientGameTest" }.configureEach {
    property("veyrath.tourSeeds", providers.gradleProperty("veyrath.tourSeeds").getOrElse("1,2,3"))
    // Comma list of locator targets instead of the full tour ("none" = spawn and Crown study only).
    property("veyrath.tourTargets", providers.gradleProperty("veyrath.tourTargets").getOrElse(""))
    property("veyrath.tourSetup", providers.gradleProperty("veyrath.tourSetup")
        .getOrElse(if (fullPack) "fullpack" else if (devMods) "devmods" else "vanilla"))
}

sourceSets.main {
    // Generated biome JSON (gradlew :mod-fabric:runDatagen) is committed and packaged. Loom 1.17
    // already adds src/main/generated as a resource dir; only the datagen cache is excluded.
    resources.exclude(".cache/**")
    if (!withDh) java.exclude("dev/veyrath/mod/dh/**")
}

dependencies {
    minecraft("com.mojang:minecraft:${libs.versions.minecraft.get()}")
    implementation("net.fabricmc:fabric-loader:${libs.versions.fabric.loader.get()}")
    implementation("net.fabricmc.fabric-api:fabric-api:${libs.versions.fabric.api.get()}")

    // Engine is bundled into the mod jar (jar-in-jar).
    implementation(project(":engine"))
    include(project(":engine"))

    // Distant Horizons: compile-time only; DH stays optional at runtime. The DH mod jar contains
    // its API classes ("automatically included in the DH mod jar"), and DH 3.3.4-26.3 is a
    // verified Modrinth version, whereas a separate DistantHorizonsApi 7.2.0 artifact is not
    // confirmed to exist. Fallback if this does not resolve:
    //   compileOnly("maven.modrinth:DistantHorizonsApi:${libs.versions.distanthorizons.api.get()}")
    if (withDh) compileOnly("maven.modrinth:distanthorizons:${libs.versions.distanthorizons.asProvider().get()}")

    if (devMods) {
        // UNVERIFIED: configuration name (Loom's non-remapping counterpart of modLocalRuntime) and
        // Modrinth version strings; string form so a wrong name only fails when the flag is set.
        for (dep in listOf(
            "maven.modrinth:sodium:mc26.3-0.9.2-fabric",
            "maven.modrinth:iris:1.11.7+26.3-fabric",
            "maven.modrinth:distanthorizons:3.3.4-26.3",
        )) add("localRuntime", dep)
    }
    if (fullPack) {
        val jars = fileTree(packMods) {
            include("*.jar")
            exclude("veyrath-*.jar", "fabric-api-*.jar")
        }
        if (jars.isEmpty) throw GradleException("-Pveyrath.fullPack: no mod jars in $packMods (run tools/install first)")
        add("localRuntime", jars)
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
