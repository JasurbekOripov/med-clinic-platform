package uz.medclinic.vendor.devices.api

import uz.medclinic.vendor.shared.security.Actor
import uz.medclinic.vendor.shared.security.ActorType
import java.io.Serial
import java.io.Serializable
import java.time.Instant
import java.util.UUID

/** The authenticated Clinic Server of a device API request (principal of the device security chain). */
data class AuthenticatedDevice(
    val deviceId: UUID,
    val clinicId: UUID,
    val branchId: UUID,
    val hostname: String,
) : Actor, Serializable {
    override val actorType: ActorType get() = ActorType.DEVICE
    override val actorId: UUID get() = deviceId
    override val actorName: String get() = "device $hostname"

    private companion object {
        @Serial
        private const val serialVersionUID: Long = 1L
    }
}

data class DeviceInfo(
    val id: UUID,
    val clinicId: UUID,
    val branchId: UUID,
    val hostname: String,
    val os: String,
    val appVersion: String,
    val pairedAt: Instant,
    val lastSeenAt: Instant?,
)

interface DeviceQueries {
    fun activeDevices(): List<DeviceInfo>
    fun isActive(deviceId: UUID): Boolean

    /** Heartbeat interval announced to Clinic Servers. */
    fun heartbeatIntervalSeconds(): Int
}
