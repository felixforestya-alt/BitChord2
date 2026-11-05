// A build of its own, outside the app's: it compiles the app's pure-logic files straight from the
// source tree, with their main-branch versions beside them, so a change can be measured against
// what it replaced in one JVM. Needs Maven Central and the Gradle plugin portal only.
pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "bitchord-perf-logic"
