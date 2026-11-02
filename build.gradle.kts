allprojects {
    group = providers.gradleProperty("veyrath.group").get()
    version = providers.gradleProperty("veyrath.version").get()
    repositories {
        mavenCentral()
    }
}

subprojects {
    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        }
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.release.set(25)
            options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-serial", "-Xlint:-processing"))
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            maxHeapSize = "3g"
            testLogging {
                events("passed", "failed", "skipped")
                showStandardStreams = true
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }
    }
}
