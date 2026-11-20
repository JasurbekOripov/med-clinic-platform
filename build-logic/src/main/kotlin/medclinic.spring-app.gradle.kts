import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("medclinic.kotlin-library")
    id("org.jetbrains.kotlin.plugin.spring")
    id("org.springframework.boot")
}

val libs = the<LibrariesForLibs>()

dependencies {
    "implementation"(platform(libs.spring.modulith.bom))
    "testImplementation"(platform(libs.spring.modulith.bom))
}
