plugins {
    id("medclinic.spring-app")
}

dependencies {
    implementation(project(":libs:common"))
    implementation(project(":libs:license"))
    implementation(project(":libs:vendor-protocol"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-session-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(libs.springdoc.webmvc.ui)

    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation(libs.springmockk)
}

tasks.bootJar {
    archiveFileName.set("vendor-platform.jar")
}

// The React console is built separately (pnpm build) and copied into the jar when present.
val webDist = rootProject.layout.projectDirectory.dir("vendor-platform/web/dist")
tasks.processResources {
    from(webDist) {
        into("static")
    }
}
