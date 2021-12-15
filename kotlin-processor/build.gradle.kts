plugins {
    kotlin("jvm")
}

group = "com.johngachihi"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.google.devtools.ksp:symbol-processing-api:1.6.0-1.0.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing-ksp:1.4.6")
    testImplementation("org.assertj:assertj-core:3.21.0")
}