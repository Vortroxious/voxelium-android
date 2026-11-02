import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Voxelium's launcher core, ported from the iOS app's Swift VoxeliumCore.
// Plain Kotlin/JVM with no Android dependencies, so its tests run on the build machine.
plugins {
    alias(libs.plugins.kotlin.jvm)
    kotlin("plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
}

tasks.test {
    // Opt-in tests that use the real network: -Pvoxelium.networkTests=true
    systemProperty("voxelium.networkTests", project.findProperty("voxelium.networkTests") ?: "false")
}
