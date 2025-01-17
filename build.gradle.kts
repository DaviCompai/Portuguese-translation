import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.ajoberstar.grgit.*

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.21")
    }
}

plugins {
    id("java-library")
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("java")
    kotlin("jvm") version "1.6.10"
    id("org.ajoberstar.grgit") version "5.2.0"
    id("com.modrinth.minotaur") version "2.+"
}

apply(plugin = "java")
apply(plugin = "java-library")
apply(plugin = "com.github.johnrengelman.shadow")
apply(plugin = "kotlin")

group = "me.iru"
val pluginName: String by project
val pluginVersion: String by project
val minecraftVersion: String by project

tasks {

    withType<KotlinCompile>() {
        kotlinOptions.jvmTarget = "1.8"
    }

    val makeDefaults by registering(Copy::class) {
        dependsOn(processResources)

        from("src/main/resources/lang/")
        include("**/*.yml")
        into("build/resources/main/lang/defaults/")
    }

    processResources {
        outputs.upToDateWhen { false }

        filesMatching("**plugin.yml") {
            expand(
                mutableMapOf(
                    Pair("pluginVersion", pluginVersion),
                    Pair("minecraftVersion", minecraftVersion)
                )
            )
        }

    }

    shadowJar {
        dependsOn(processResources)
        dependsOn(makeDefaults)
        archiveFileName.set("${pluginName}-${pluginVersion}.jar")
        relocate("org.bstats", group)
    }
}

tasks.register("createReleases") {
    dependsOn(tasks["createGitHubRelease"])
    dependsOn(tasks.modrinth)
    dependsOn(tasks.modrinthSyncBody)
}

modrinth {
    val version = pluginVersion

    token.set(System.getenv("MODRINTH_TOKEN"))
    projectId.set("authy")
    versionNumber.set(version)
    versionType.set("release")
    versionName.set("$pluginName $version")
    uploadFile.set(tasks.shadowJar as Any)
    gameVersions.addAll("1.17", "1.18", "1.19", "1.20")
    loaders.addAll("spigot", "paper", "purpur")
    changelog.set(rootProject.file("changelog.md").readText())
    syncBodyFrom.set(rootProject.file("README.md").readText())
}

tasks.register("createGitHubRelease") {
    dependsOn(tasks.shadowJar)
    doLast {
        val git = Grgit.open {
            dir = projectDir
        }
        val tagName = "v${pluginVersion}"
        git.tag.add {
            name = tagName
            message = "Release $tagName"
            force = true
        }
        git.push {
            tags = true
        }
        git.close()

        val command = "gh release create $tagName -F changelog.md -t \"$tagName\" \"build/libs/${pluginName}-${pluginVersion}.jar\""
        val process = ProcessBuilder(command.split(" ")).start()
        process.waitFor()
    }
}


repositories {
    mavenCentral()
    mavenLocal()
    maven(url = "https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {

    compileOnly("org.apache.logging.log4j:log4j-api:2.20.0")
    compileOnly("org.apache.logging.log4j:log4j-core:2.20.0")

    runtimeOnly("mysql:mysql-connector-java:8.0.33")

    implementation("org.spigotmc:spigot-api:${minecraftVersion}-R0.1-SNAPSHOT")
    implementation("org.bstats:bstats-bukkit:3.0.2")
    implementation(kotlin("stdlib-jdk8"))
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}