plugins {
    kotlin("jvm") version "1.9.22"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "me.miku.backup"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper API
    compileOnly("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.3")

    // Cron utils
    implementation("com.cronutils:cron-utils:9.2.1")

    // Google Drive API
    implementation("com.google.api-client:google-api-client:2.0.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20230815-2.0.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.19.0")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        relocate("kotlin", "me.miku.backup.libs.kotlin")
        relocate("kotlinx.coroutines", "me.miku.backup.libs.coroutines")
        relocate("com.cronutils", "me.miku.backup.libs.cronutils")
        relocate("com.google", "me.miku.backup.libs.google")
    }
    
    build {
        dependsOn(shadowJar)
    }
}
