import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

val libs = the<LibrariesForLibs>()

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        allWarningsAsErrors.set(true)
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

dependencies {
    "implementation"(platform(libs.spring.boot.bom))
    "testImplementation"(platform(libs.spring.boot.bom))
    "testImplementation"("org.junit.jupiter:junit-jupiter")
    "testImplementation"("org.junit.jupiter:junit-jupiter-params")
    "testImplementation"("org.assertj:assertj-core")
    "testImplementation"(libs.mockk)
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("-XX:+EnableDynamicAgentLoading", "-Xshare:off")
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
