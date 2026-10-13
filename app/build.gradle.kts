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

// Renders every screen with sample data into app/build/screenshots (hidden window, no user data touched).
tasks.register<JavaExec>("screenshots") {
    group = "verification"
    description = "Saves a PNG of every UI screen to build/screenshots for comparison with docs/design."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    args("--screenshots", layout.buildDirectory.dir("screenshots").get().asFile.path)
}

// The UI tokens live once in docs/design and are bundled into the app from there.
tasks.processResources {
    from(rootProject.file("docs/design/tokens.json")) { into("assets/ui") }
}
