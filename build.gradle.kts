import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.archivesName

plugins {
    `java-library`
    kotlin("jvm") version "1.9.23"
    id("io.github.goooler.shadow") version("8.1.7")
    id("io.papermc.paperweight.userdev") version("1.6.0") apply(false)
    id("xyz.jpenilla.run-paper") version "2.2.4"
    id("org.jetbrains.dokka") version "1.9.20"
}


val minecraft = "1.20.6"
val folia = "1.20.4" // TODO Bumps version.
val adventure = "4.16.0"
val platform = "4.3.2"
val targetJavaVersion = 21

allprojects {
    apply(plugin = "java")
    apply(plugin = "kotlin")
    apply(plugin = "org.jetbrains.dokka")
    group = "kr.toxicity.healthbar"
    version = "3.0"
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.opencollab.dev/main/")
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        maven("https://mvn.lumine.io/repository/maven-public/")
    }
    dependencies {
        compileOnly("io.lumine:Mythic-Dist:5.6.2")

        testImplementation(kotlin("test"))
    }
    tasks {
        test {
            useJUnitPlatform()
        }
        compileJava {
            options.encoding = Charsets.UTF_8.name()
        }
    }
}

fun Project.spigot() = also {
    it.dependencies {
        compileOnly("org.spigotmc:spigot-api:$minecraft-R0.1-SNAPSHOT")
        compileOnly("net.kyori:adventure-api:$adventure")
        compileOnly("net.kyori:adventure-platform-bukkit:$platform")
    }
}

fun Project.dependency(name: String) = also {
    it.dependencies {
        compileOnly(name)
    }
}

val api = project("api").spigot()

fun getApiDependencyProject(name: String) = project(name).also {
    it.dependencies {
        compileOnly(api)
    }
}

val dist = getApiDependencyProject("dist").spigot().also {
    it.tasks.processResources {
        filteringCharset = Charsets.UTF_8.name()
        val props = mapOf(
            "version" to project.version,
            "adventure" to adventure,
            "platform" to platform
        )
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}

fun getProject(name: String) = getApiDependencyProject(name).also {
    dist.dependencies {
        compileOnly(it)
    }
}

fun Project.folia() = also {
    it.dependencies {
        compileOnly("dev.folia:folia-api:$folia-R0.1-SNAPSHOT")
    }
}

class NmsVersion(val name: String) {
    val project = getProject("nms:$name").also {
        it.apply(plugin = "io.papermc.paperweight.userdev")
    }
}

val legacyNms = listOf(
    NmsVersion("v1_19_R3"),
    NmsVersion("v1_20_R1"),
    NmsVersion("v1_20_R2"),
    NmsVersion("v1_20_R3")
)

val currentNms = listOf(
    NmsVersion("v1_20_R4")
)

dependencies {
    implementation(api)
    implementation(dist)
    implementation(getProject("scheduler:standard").spigot())
    implementation(getProject("scheduler:folia").folia())
    implementation(getProject("bedrock:geyser").spigot().dependency("org.geysermc.geyser:api:2.2.0-SNAPSHOT"))
    implementation(getProject("bedrock:floodgate").spigot().dependency("org.geysermc.floodgate:api:2.2.2-SNAPSHOT"))
    implementation(getProject("modelengine:legacy").spigot().dependency("com.ticxo.modelengine:api:R3.2.0"))
    implementation(getProject("modelengine:current").spigot().dependency("com.ticxo.modelengine:ModelEngine:R4.0.6"))
    legacyNms.forEach {
        implementation(project(":nms:${it.name}", configuration = "reobf"))
    }
    currentNms.forEach {
        implementation(it.project)
    }
}

val sourceJar by tasks.creating(Jar::class.java) {
    dependsOn(tasks.classes)
    fun getProjectSource(project: Project): Array<File> {
        return if (project.subprojects.isEmpty()) project.sourceSets.main.get().allSource.srcDirs.toTypedArray() else ArrayList<File>().apply {
            project.subprojects.forEach {
                addAll(getProjectSource(it))
            }
        }.toTypedArray()
    }
    archiveClassifier = "source"
    from(*getProjectSource(project))
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
val dokkaJar by tasks.creating(Jar::class.java) {
    dependsOn(tasks.dokkaHtmlMultiModule)
    archiveClassifier = "dokka"
    from(layout.buildDirectory.dir("dokka${File.separatorChar}htmlMultiModule").orNull?.asFile)
}

tasks {
    jar {
        dependsOn(clean)
        finalizedBy(shadowJar)
    }
    runServer {
        version(minecraft)
    }
    shadowJar {
        legacyNms.forEach {
            dependsOn("nms:${it.name}:reobfJar")
        }
        currentNms.forEach {
            dependsOn("nms:${it.name}:jar")
        }
        archiveClassifier = ""
        fun prefix(pattern: String) {
            relocate(pattern, "${project.group}.shaded.$pattern")
        }
        prefix("kotlin")
        dependencies {
            exclude(dependency("org.jetbrains:annotations:13.0"))
        }
        finalizedBy(sourceJar)
        finalizedBy(dokkaJar)
    }
}

ArrayList<Project>().apply {
    add(project)
    addAll(currentNms.map {
        it.project
    })
}.forEach {
    it.java {
        toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    }
    it.kotlin {
        jvmToolchain(targetJavaVersion)
    }
}