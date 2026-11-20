plugins {
    id("medclinic.kotlin-library")
}

// Pure library shared by the Vendor Platform (signing) and the Clinic Server (offline verification).
// Only JDK crypto (Ed25519 is built into Java 15+) and Jackson; no Spring.
dependencies {
    api("tools.jackson.core:jackson-databind")
    implementation("tools.jackson.module:jackson-module-kotlin")
}
