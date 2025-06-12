plugins {
    id("java-library")
    id("maven-publish")
    id("com.gradleup.shadow") version "8.3.6"
    id("xyz.jpenilla.run-velocity") version "2.3.1"
}

group = "com.lyttledev"
version = property("pluginVersion") as String
description = "LyttleProxyRecovery"
java.sourceCompatibility = JavaVersion.VERSION_21

repositories {
    mavenLocal()
    maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
    maven { url = uri("https://oss.sonatype.org/content/groups/public/") }
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://repo.maven.apache.org/maven2/") }
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    configurations = listOf(project.configurations.runtimeClasspath.get())
    dependencies {
        include(dependency("com.lyttledev:lyttleutils"))
    }
}

tasks.named<Jar>("jar") {
    enabled = false
}

tasks.named("build") {
    dependsOn("shadowJar")
}

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}

tasks {
  runVelocity {
    // Configure the Velocity version for our task.
    // This is the only required configuration besides applying the plugin.
    // Your plugin's jar (or shadowJar if present) will be used automatically.
    velocityVersion("3.4.0-SNAPSHOT")
  }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}