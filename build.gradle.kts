plugins {
    java
}

subprojects {
    apply(plugin = "java")

    group = "de.lobbysystem"
    version = "1.0.2"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    dependencies {
        // Minimum: Paper 26.2 (läuft auch auf 26.3+)
        "compileOnly"("io.papermc.paper:paper-api:26.2.build.135-stable")
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    tasks.named<ProcessResources>("processResources") {
        filteringCharset = "UTF-8"
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("plugin.yml") { expand(props) }
    }
}
