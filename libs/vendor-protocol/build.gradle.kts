plugins {
    id("medclinic.kotlin-library")
}

// Wire contract between the Clinic Server and the Vendor Platform. Plain Kotlin DTOs, no dependencies:
// both sides serialise them with their own Jackson mapper. Additive changes only; clients ignore unknown fields.
