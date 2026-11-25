plugins {
    id("medclinic.spring-app")
}

dependencies {
    implementation(project(":libs:common"))
    implementation(project(":libs:license"))
    implementation(project(":libs:vendor-protocol"))
    implementation(project(":libs:spring-support"))

    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-session-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(libs.springdoc.webmvc.ui)
    implementation(libs.poi.ooxml)

    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
}

springBoot {
    // Exposes the release version to the heartbeat and the UI.
    buildInfo()
}

tasks.bootJar {
    archiveFileName.set("clinic-server.jar")
}

val webDist = rootProject.layout.projectDirectory.dir("clinic-server/web/dist")
tasks.processResources {
    from(webDist) {
        into("static")
    }
}
