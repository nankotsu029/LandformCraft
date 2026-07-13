import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test

plugins {
    java
    application
    id("com.gradleup.shadow") version "9.5.1"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
}

dependencies {
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.21.3"))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("com.networknt:json-schema-validator:2.0.4")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.17")

    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.19")

    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    withSourcesJar()
}

application {
    mainClass = "com.github.nankotsu029.landformcraft.cli.LandformCraftCli"
    applicationName = "landformcraft"
}

tasks {
    withType<JavaCompile>().configureEach {
        options.release = 21
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    }

    withType<Test>().configureEach {
        useJUnitPlatform()
    }

    processResources {
        val properties = mapOf("version" to project.version)
        inputs.properties(properties)
        filesMatching("plugin.yml") {
            expand(properties)
        }
        from("schemas") {
            into("schemas")
        }
    }

    jar {
        // CLI distribution用。Paperへ配置するJARはclassifierなしのshadowJarを使用する。
        archiveBaseName = "LandformCraft"
        archiveClassifier = "cli"
    }

    shadowJar {
        archiveBaseName = "LandformCraft"
        archiveClassifier = ""
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        mergeServiceFiles()
        relocate("com.fasterxml.jackson", "com.github.nankotsu029.landformcraft.internal.jackson")
        relocate("com.networknt", "com.github.nankotsu029.landformcraft.internal.jsonschema")
        relocate("com.ethlo", "com.github.nankotsu029.landformcraft.internal.ethlo")
        relocate("org.yaml.snakeyaml", "com.github.nankotsu029.landformcraft.internal.snakeyaml")
        relocate("org.slf4j", "com.github.nankotsu029.landformcraft.internal.slf4j")
        exclude(
            "META-INF/*.SF",
            "META-INF/*.RSA",
            "META-INF/*.DSA",
            "META-INF/LICENSE",
            "META-INF/LICENSE.txt",
            "META-INF/NOTICE"
        )
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion("1.21.11")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    test {
        dependsOn(shadowJar)
        val pluginJar = layout.buildDirectory.file("libs/LandformCraft-${project.version}.jar")
        inputs.file(pluginJar).withPropertyName("pluginJar")
        systemProperty("landformcraft.pluginJar", pluginJar.get().asFile.absolutePath)
    }
}
