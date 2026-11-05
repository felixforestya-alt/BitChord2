plugins { id("org.jetbrains.kotlin.jvm") version "2.3.20" }

dependencies { testImplementation(kotlin("test")) }

kotlin { jvmToolchain(21) }

// The scripting plugin is not needed to compile plain Kotlin, and offline it is one artifact fewer.
configurations.matching { it.name.startsWith("kotlinCompilerPluginClasspath") }.configureEach {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-compiler-embeddable")
}

tasks.withType<Test> {
    systemProperty("java.awt.headless", "true")
    // A preferences store of the harness's own, never the listener's.
    systemProperty("java.util.prefs.userRoot", layout.buildDirectory.dir("prefs-root").get().asFile.absolutePath)
    maxHeapSize = "2g"
    testLogging {
        showStandardStreams = true
        events("passed", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    outputs.upToDateWhen { false }
}
