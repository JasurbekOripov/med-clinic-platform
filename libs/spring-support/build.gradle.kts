plugins {
    id("medclinic.spring-library")
}

// Cross-cutting web/persistence building blocks shared by vendor-platform and clinic-server:
// problem+json errors, paging, request ids, actor resolution, JDBC helpers, SPA serving.
dependencies {
    api("org.springframework.boot:spring-boot-starter-webmvc")
    api("org.springframework.boot:spring-boot-starter-security")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api("tools.jackson.module:jackson-module-kotlin")
    compileOnly(libs.springdoc.webmvc.ui)
}
