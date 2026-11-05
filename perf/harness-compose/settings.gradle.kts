// Compose models of what was changed in the app's composables, rendered with ImageComposeScene.
// Compose Multiplatform 1.5.12: from 1.6 on, Compose's desktop artifacts depend on androidx ones
// published to Google's Maven alone, which the machine these were measured on could not reach.
pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "bitchord-perf-compose"
