package uz.medclinic.vendor.shared

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/** `shared` is an open module: cross-cutting building blocks every module may use. It depends on no module. */
@PackageInfo
@ApplicationModule(displayName = "Shared kernel", type = ApplicationModule.Type.OPEN)
class ModuleMetadata
