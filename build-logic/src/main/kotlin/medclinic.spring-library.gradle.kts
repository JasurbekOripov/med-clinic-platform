import org.gradle.accessors.dm.LibrariesForLibs

// A library with Spring beans (auto-configuration), shared by the Spring Boot applications.
plugins {
    id("medclinic.kotlin-library")
    id("org.jetbrains.kotlin.plugin.spring")
}

val libs = the<LibrariesForLibs>()

dependencies {
    "api"(platform(libs.spring.boot.bom))
}
