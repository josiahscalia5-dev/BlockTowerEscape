import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Headless playtest for Screen 4: runs the platform-independent game core on the JVM with a
// Java2D renderer and an autopilot. Not part of the APK.
//   ./gradlew :sim:run --args="play"            autopilot START -> Level Complete, prints a report
//   ./gradlew :sim:run --args="play frames=out" also writes rendered frames to out/
plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

sourceSets {
    main {
        kotlin.srcDir("../app/src/main/java/com/blocktower/escape/core")
    }
}

application {
    mainClass.set("com.blocktower.escape.sim.MainKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootDir
    jvmArgs("-Djava.awt.headless=true", "-Xmx2g")
}
