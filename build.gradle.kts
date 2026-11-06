plugins {
    java
}

group = "de.vincent"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io") {
        content { includeGroup("com.github.NEZNAMY") }
    }
}

val velocityApi = (findProperty("velocityApi") ?: "3.4.0").toString()

dependencies {
    // Compiled against the oldest supported version (1.21.11) so the jar also runs on 26.1.x.
    // Check against 26.1 with: ./gradlew compileJava -PpaperApi=26.1.2.build.+
    compileOnly("io.papermc.paper:paper-api:" + (findProperty("paperApi") ?: "1.21.11-R0.1-SNAPSHOT"))

    // The same jar is also a Velocity plugin (bridge to TAB on the proxy).
    compileOnly("com.velocitypowered:velocity-api:$velocityApi")
    annotationProcessor("com.velocitypowered:velocity-api:$velocityApi")

    // TAB API (optional at runtime, used on the backend and on the proxy).
    compileOnly("com.github.NEZNAMY:TAB-API:6.1.2")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    // Paper 1.21.11 runs on Java 21, 26.1 on Java 25 -> emit Java 21 bytecode.
    // (The 26.1 API itself requires Java 25, so the compatibility check build uses 25.)
    options.release.set(if (project.hasProperty("paperApi")) 25 else 21)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}
