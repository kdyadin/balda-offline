plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}

tasks.withType<Test> {
    // Тесты производительности читают словарь прямо из assets приложения.
    systemProperty("balda.assetsDir", rootProject.file("app/src/main/assets").absolutePath)
    testLogging { events("failed"); showStandardStreams = false }
}
