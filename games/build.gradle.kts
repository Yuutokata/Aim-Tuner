plugins {
    id("aimtune.kotlin-serialization-conventions")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.serialization.json)
}
