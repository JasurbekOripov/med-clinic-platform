package uz.medclinic.license

import java.time.Instant
import java.util.UUID

internal object LicenseFixtures {
    val CLINIC_ID: UUID = UUID.fromString("0193a000-0000-7000-8000-000000000001")

    fun payload(
        validFrom: Instant = Instant.parse("2026-01-01T19:00:00Z"),
        validUntil: Instant = Instant.parse("2027-01-01T19:00:00Z"),
        graceDays: Int = 14,
        modules: List<String> = listOf(LicenseModules.REGISTRATION, LicenseModules.CASHIER),
    ) = LicensePayload(
        licenseId = UUID.fromString("0193a000-0000-7000-8000-0000000000aa"),
        serial = 7,
        clinicId = CLINIC_ID,
        clinicCode = "CL-0001",
        clinicName = "Shifo Med",
        plan = "STANDARD",
        modules = modules,
        maxUsers = 25,
        maxBranches = 1,
        validFrom = validFrom,
        validUntil = validUntil,
        expiryGraceDays = graceDays,
        issuedAt = Instant.parse("2026-01-01T10:15:30.123456Z"),
    )
}
