plugins {
    id("aimtune.kotlin-conventions")
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":games"))
    implementation(project(":input"))
    implementation(project(":storage"))
}

application {
    mainClass.set("de.yuuto.aimtune.app.MainKt")
}
