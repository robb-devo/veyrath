rootProject.name = "veyrath"

// Platform-independent world engine (pure Java, no Minecraft dependency).
include("engine")
// Headless preview renderer, gallery generator and benchmark harness for the engine.
include("preview")

// The Fabric mod needs the Minecraft and Fabric maven servers. Builds on machines that
// cannot reach them (e.g. restricted CI) pass -Pveyrath.skipMod=true.
if (providers.gradleProperty("veyrath.skipMod").orNull != "true") {
    include("mod-fabric")
}
