package uz.medclinic.clinic.settings.api

/** Called once by the first-run setup, after pairing, with the clinic name known to the vendor. */
interface SettingsSetup {
    fun initialise(clinicName: String)
}
