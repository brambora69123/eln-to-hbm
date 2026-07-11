plugins {
    id("com.gtnewhorizons.gtnhconvention")
    kotlin("jvm")
}

// Dependencies bundled into our mod jar. Forge 1.7.10 loads mods with isolated
// classloaders, so we cannot rely on ELN's shaded kotlin / shared deps at runtime.
val shade = configurations.create("shade")

dependencies {
    shade(kotlin("stdlib"))
    shade("org.semver4j:semver4j:4.3.0")
    shade("org.apache.commons:commons-math3:3.6.1")
}

tasks.named<Jar>("jar") {
    shade.files.forEach { f ->
        from(if (f.isDirectory) f else zipTree(f))
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
