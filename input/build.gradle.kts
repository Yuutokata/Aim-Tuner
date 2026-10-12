plugins {
    id("aimtune.kotlin-conventions")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.jna)
    implementation(libs.jna.platform)
}
