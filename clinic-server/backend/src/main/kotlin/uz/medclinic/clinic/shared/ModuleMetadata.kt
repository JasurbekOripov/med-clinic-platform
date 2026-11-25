package uz.medclinic.clinic.shared

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/** Clinic-wide vocabulary (permission catalogue, OpenAPI metadata) every module may use. */
@PackageInfo
@ApplicationModule(displayName = "Shared kernel", type = ApplicationModule.Type.OPEN)
class ModuleMetadata
