plugins {
    `java-library`
}

description = "VEYRATH world engine: deterministic, Minecraft-independent world generation core."

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
