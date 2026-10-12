plugins {
    id("aimtune.kotlin-conventions")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.sqlite.jdbc)
}
