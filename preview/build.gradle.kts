plugins {
    application
}

description = "Headless renderer, test-seed gallery and benchmark harness for the VEYRATH world engine."

dependencies {
    implementation(project(":engine"))
    implementation(project(":combat"))
}

application {
    mainClass.set("dev.veyrath.preview.PreviewMain")
    applicationDefaultJvmArgs = listOf("-Xmx8G", "-XX:+UseG1GC")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    maxHeapSize = "8g"
}
