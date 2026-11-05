plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
}

val cmp = "1.5.12"

dependencies {
    implementation("org.jetbrains.compose.ui:ui-desktop:$cmp")
    implementation("org.jetbrains.compose.foundation:foundation-desktop:$cmp")
    implementation("org.jetbrains.compose.animation:animation-desktop:$cmp")
    implementation("org.jetbrains.compose.material:material-desktop:$cmp")
    implementation("org.jetbrains.skiko:skiko-awt-runtime-linux-x64:0.7.85.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3")
    testImplementation(kotlin("test"))
}

configurations.matching { it.name.startsWith("kotlinCompilerPluginClasspath") }.configureEach {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-compiler-embeddable")
}

kotlin { jvmToolchain(21) }

tasks.withType<Test> {
    systemProperty("java.awt.headless", "true")
    maxHeapSize = "2g"
    testLogging {
        showStandardStreams = true
        events("passed", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    outputs.upToDateWhen { false }
}
