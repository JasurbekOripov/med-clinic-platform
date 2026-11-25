package uz.medclinic.clinic.vendorlink.application

import org.springframework.stereotype.Service
import uz.medclinic.clinic.shared.ClinicContext
import uz.medclinic.clinic.shared.ClinicIdentity
import uz.medclinic.clinic.vendorlink.infrastructure.Installation
import uz.medclinic.clinic.vendorlink.infrastructure.JdbcInstallationRepository

/** Who this server is. Cached: the identity never changes once paired. */
@Service
class InstallationService(private val repository: JdbcInstallationRepository) : ClinicContext {
    @Volatile
    private var cached: Installation? = null

    fun installation(): Installation? = cached ?: repository.find().also { cached = it }

    override fun currentOrNull(): ClinicIdentity? =
        installation()?.let { ClinicIdentity(it.clinicId, it.branchId, it.clinicCode, it.branchCode) }

    /** After pairing (and in tests that reset the database). */
    fun refresh() {
        cached = repository.find()
    }
}
