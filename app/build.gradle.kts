plugins {
    id("aimtune.kotlin-conventions")
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":games"))
    implementation(project(":input"))
    implementation(project(":storage"))

    implementation(libs.gdx)
    implementation(libs.gdx.backend.lwjgl3)
    implementation(libs.gdx.freetype)
    runtimeOnly(variantOf(libs.gdx.platform) { classifier("natives-desktop") })
    runtimeOnly(variantOf(libs.gdx.freetype.platform) { classifier("natives-desktop") })
}

application {
    mainClass.set("de.yuuto.aimtune.app.MainKt")
}
