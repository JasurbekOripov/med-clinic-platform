plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.allopen)
    implementation(libs.spring.boot.gradle.plugin)
    // Exposes the type-safe `libs` catalog accessors to precompiled convention plugins.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
