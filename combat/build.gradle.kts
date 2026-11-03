plugins {
    `java-library`
}

description = "VEYRATH combat core: input, moves, state machine, animation and hit detection (no Minecraft dependency)."

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
