plugins {
    id("aimtune.kotlin-serialization-conventions")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.sqlite.jdbc)
    implementation(libs.kotlinx.serialization.json)
}
