plugins {
    id("com.gtnewhorizons.gtnhconvention")
    kotlin("jvm")
    id("com.gradleup.shadow")
}

// Dependencies bundled into our mod jar.
//
// Forge 1.7.10 uses a SINGLE shared mod classloader (mods are NOT isolated), so
// every mod's classes live on one classpath. ELN relocates its bundled kotlin to
// `mods.eln.shadow.kotlin.*`, which means any consumer calling ELN's API that
// touches kotlin types (e.g. SimpleShaftDescriptor's `KClass` params) must use the
// exact same relocated package. To match, we relocate our own bundled kotlin-stdlib
// to `mods.eln.shadow.kotlin` so the bridge's `::class` references resolve to the
// same `KClass` class ELN expects. semver4j / commons-math3 are bundled un-relocated
// (ELN does not ship those, so there is no clash).
val shade = configurations.create("shade")

dependencies {
    shade(kotlin("stdlib"))
    shade("org.semver4j:semver4j:4.3.0")
    shade("org.apache.commons:commons-math3:3.6.1")
}

val shadowJar = tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    configurations = listOf(shade)
    archiveClassifier.set("")
    relocate("kotlin.", "mods.eln.shadow.kotlin.")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/INDEX.LIST", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

// The default `jar` is replaced by the relocated shadow jar; reobf that instead.
tasks.named("jar") { enabled = false }
tasks.named("assemble").configure { dependsOn(shadowJar) }

// RFG auto-creates `reobfShadowJar` from the shadowJar task above.
tasks.named("build").configure { dependsOn("reobfShadowJar") }
