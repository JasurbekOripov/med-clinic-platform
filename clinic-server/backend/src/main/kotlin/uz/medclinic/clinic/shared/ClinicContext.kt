package uz.medclinic.clinic.shared

import java.util.UUID

/** Which clinic and branch this server belongs to (known after pairing with the Vendor Platform). */
data class ClinicIdentity(val clinicId: UUID, val branchId: UUID, val clinicCode: String, val branchCode: String)

/**
 * Every row carries `clinic_id` and `branch_id` so branches can later be synchronised with an HQ server.
 * Implemented by the vendorlink module; defined here so any module can use it without a module cycle.
 */
interface ClinicContext {
    fun currentOrNull(): ClinicIdentity?

    fun current(): ClinicIdentity = currentOrNull() ?: error("This server is not paired with the Vendor Platform yet")
}
