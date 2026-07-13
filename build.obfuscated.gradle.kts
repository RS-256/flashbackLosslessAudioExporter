plugins {
    id("net.fabricmc.fabric-loom-remap")
    // id("me.modmuss50.mod-publish-plugin")  // uncomment to enable publishing
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = property("mod.id") as String

val requiredJava = when {
    sc.current.parsed >= "26.1"   -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    sc.current.parsed >= "1.18"   -> JavaVersion.VERSION_17
    sc.current.parsed >= "1.17"   -> JavaVersion.VERSION_16
    else                          -> JavaVersion.VERSION_1_8
}

// ---------------------------------------------------------------
// Repositories
// ---------------------------------------------------------------
repositories {
    /**
     * Restricts dependency search of the given [groups] to the [maven URL][url],
     * improving the setup speed.
     */
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }
    strictMaven("https://www.cursemaven.com",     "CurseForge", "curse.maven")
    strictMaven("https://api.modrinth.com/maven", "Modrinth",   "maven.modrinth")
    mavenCentral() // com.moulberry:lattice
}

// ---------------------------------------------------------------
// Dependencies
// ---------------------------------------------------------------
dependencies {
    /**
     * To use only the Fabric API modules you need instead of the full API:
     *
     *   fun fapi(vararg modules: String) {
     *       for (it in modules) modImplementation(fabricApi.module(it, property("deps.fabric_api") as String))
     *   }
     *   fapi("fabric-lifecycle-events-v1", "fabric-resource-loader-v0")
     *
     * See https://github.com/FabricMC/fabric for the full module list.
     */

    minecraft("com.mojang:minecraft:${sc.current.version}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")

    // Flashback: mixin target. Compile/runtime reference only — never include()d (FR-08, NFR-05).
    modImplementation("maven.modrinth:flashback:${property("deps.flashback")}")

    // Mod Menu: dev/test convenience only — not a dependency of this mod.
    modImplementation("maven.modrinth:modmenu:${property("deps.modmenu")}")

    // Lattice (Flashback's config-UI lib) references MC classes, so unlike the other
    // nested libs it must go through loom's intermediary->named remap for dev runs.
    modLocalRuntime("com.moulberry:lattice:${property("deps.lattice")}")
}

// ---------------------------------------------------------------
// Flashback nests its library mods (mixinconstraints, mixinsquared,
// lattice, ...) inside META-INF/jars. Loom strips nested jars when
// remapping modImplementation dependencies, which breaks Flashback's
// own mixins in dev ("Dynamic selector @MixinSquared:Handler is not
// registered", missing ConstraintsMixinPlugin). Extract them from the
// Flashback jar itself and put them back on the dev runtime classpath.
// ---------------------------------------------------------------
val flashbackForNestedJars: Configuration = configurations.create("flashbackForNestedJars") {
    isTransitive = false
}

dependencies {
    flashbackForNestedJars("maven.modrinth:flashback:${property("deps.flashback")}")
}

val extractFlashbackNestedJars = tasks.register<Sync>("extractFlashbackNestedJars") {
    // resolved eagerly at configuration time: zipTree results serialize fine with the
    // configuration cache, while deferring via providers would capture the script object
    from(flashbackForNestedJars.map { zipTree(it) }) {
        include("META-INF/jars/*.jar")
        // lattice references MC classes and must be remapped instead: see modLocalRuntime above
        exclude("META-INF/jars/lattice-*.jar")
        eachFile { path = name }
    }
    includeEmptyDirs = false
    into(layout.buildDirectory.dir("flashback-nested-jars"))
}

dependencies {
    runtimeOnly(
        files(layout.buildDirectory.dir("flashback-nested-jars").map { dir ->
            dir.asFileTree.matching { include("*.jar") }
        }).builtBy(extractFlashbackNestedJars)
    )
}

// ---------------------------------------------------------------
// Loom configuration
// ---------------------------------------------------------------
val accesswidener = when {
    sc.current.parsed >= "26.1" -> "flae.unobfuscated.accesswidener"
    else                        -> "flae.obfuscated.accesswidener"
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")
    accessWidenerPath = rootProject.file("src/main/resources/$accesswidener")

    decompilerOptions.named("vineflower") {
        options.put("mark-corresponding-synthetics", "1") // Adds names to lambdas - useful for mixins
    }

    runConfigs.all {
        ideConfigGenerated(true)
        vmArgs("-Dmixin.debug.export=true") // Exports transformed classes for debugging
        runDir = "../../run"                // Shares the run directory between versions
    }
}

// ---------------------------------------------------------------
// Java
// ---------------------------------------------------------------
java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava
}

// ---------------------------------------------------------------
// Resource processing — injects properties into JSON resources
// ---------------------------------------------------------------
val fabricApiKey =
    if (sc.current.parsed <= "1.19.2") "fabric"
    else "fabric-api"

tasks {
    processResources {
        val props = mapOf(
            "id"            to project.property("mod.id"),
            "name"          to project.property("mod.name"),
            "version"       to project.property("mod.version"),
            "minecraft"     to project.property("mod.mc_dep"),
            "fabricLoader"  to project.property("build.fabric_loader"),
            "fabricAPI"     to project.property("build.fabric_api"),
            "fabricApiKey"  to fabricApiKey,
            "accesswidener" to accesswidener
        )
        filesMatching("fabric.mod.json") { expand(props) }

        val mixinJava   = "JAVA_${requiredJava.majorVersion}"
        filesMatching("*.mixins.json") {
            expand(mapOf("java" to mixinJava))
        }
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

// ---------------------------------------------------------------
// Jar naming: <id>-v<version>-mc<mc>.jar
// ---------------------------------------------------------------
    named<AbstractArchiveTask>("remapJar") {
        archiveFileName.set(
            "${project.property("mod.id")}-v${project.property("mod.version")}-mc${sc.current.version}.jar"
        )
    }

    named<AbstractArchiveTask>("sourcesJar") {
        archiveFileName.set(
            "${project.property("mod.id")}-v${project.property("mod.version")}-mc${sc.current.version}-sources.jar"
        )
    }
// ---------------------------------------------------------------
// Build helpers: copy outputs into a shared build/libs/<version>/
// ---------------------------------------------------------------

    register<Copy>("buildAndCollect") {
        group = "build"
        from(remapJar.map { it.archiveFile }, remapSourcesJar.map { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
        dependsOn("build")
    }

    register<Copy>("buildAndCollectRemapped") {
        group = "build"
        from(remapJar.map { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}/remapped"))
        dependsOn("build")
    }

    register<Copy>("buildAndCollectSources") {
        group = "build"
        from(remapSourcesJar.map { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}/sources"))
        dependsOn("build")
    }
}

// ---------------------------------------------------------------
// Publishes builds to Modrinth and Curseforge
// with changelog from the CHANGELOG.md file only on the final release version
//
// uncomment after enabling mod-publish-plugin above
// and filling in publish.modrinth / publish.curseforge in
// root/gradle.properties.
// ---------------------------------------------------------------

/*
val changelogReleaseVersion = rootProject.extra["publish.changelogReleaseVersion"] as String
val publishChangelog =
    if (sc.current.version == changelogReleaseVersion) rootProject.file("CHANGELOG.md").readText()
    else ""

publishMods {
    file           = tasks.remapJar.flatMap { it.archiveFile }
    additionalFiles.from(tasks.remapSourcesJar.flatMap { it.archiveFile })
    displayName    = "${property("mod.name")} v${property("mod.version")} for mc${property("mod.mc_title")}"
    version        = "v${property("mod.version")}-mc${sc.current.version}"
    changelog      = publishChangelog
    type           = STABLE
    modLoaders.add("fabric")

    dryRun = providers.environmentVariable("MODRINTH_TOKEN").getOrNull() == null
        || providers.environmentVariable("CURSEFORGE_TOKEN").getOrNull() == null

// Strongly recommend that you save the token in your PC’s environment variables.

    modrinth {
        projectId   = property("publish.modrinth") as String
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
        minecraftVersions.addAll(property("mod.mc_targets").toString().split(' '))
        requires {
            slug = "fabric-api"
        }
    }

    curseforge {
        projectId   = property("publish.curseforge") as String
        accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
        minecraftVersions.addAll(property("mod.mc_targets").toString().split(' '))
        requires {
            slug = "fabric-api"
        }
    }
}

tasks.named("publishModrinth") {
    dependsOn("remapJar")
}

tasks.named("publishCurseforge") {
    dependsOn("remapJar")
}
*/
