package uz.medclinic.clinic.shared

/** Limits from the installed license that other modules enforce (implemented by vendorlink). */
interface LicenseLimits {
    /** Maximum active staff accounts, or null when no valid license is installed (then nothing can be created anyway). */
    fun maxUsers(): Int?

    fun hasModule(module: String): Boolean
}
