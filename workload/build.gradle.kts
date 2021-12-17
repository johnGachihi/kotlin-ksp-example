plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp") version "1.5.31-1.0.0"
}

group = "com.johngachihi"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":preferences-processor"))
    ksp(project(":preferences-processor"))
}

kotlin {
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
    sourceSets.test {
        kotlin.srcDir("build/generated/ksp/test/kotlin")
    }
}