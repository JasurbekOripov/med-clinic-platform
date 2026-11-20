package uz.medclinic.vendor.devices.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.HexFormat
import java.util.UUID

enum class DeviceStatus { ACTIVE, REVOKED }

data class Device(
    val id: UUID,
    val clinicId: UUID,
    val branchId: UUID,
    val tokenHash: String,
    val status: DeviceStatus,
    val hostname: String,
    val os: String,
    val appVersion: String,
    val pairedAt: Instant,
    val revokedAt: Instant?,
    val revokeReason: String?,
    val lastSeenAt: Instant?,
    val lastIp: String?,
    val version: Int,
)

enum class PairingCodeState { OPEN, CLAIMED, CANCELLED, EXPIRED }

data class PairingCode(
    val id: UUID,
    val clinicId: UUID,
    val branchId: UUID,
    val codeHash: String,
    val expiresAt: Instant,
    val createdBy: UUID,
    val createdAt: Instant,
    val claimedAt: Instant?,
    val claimedDeviceId: UUID?,
    val cancelledAt: Instant?,
) {
    fun state(now: Instant): PairingCodeState = when {
        claimedAt != null -> PairingCodeState.CLAIMED
        cancelledAt != null -> PairingCodeState.CANCELLED
        !now.isBefore(expiresAt) -> PairingCodeState.EXPIRED
        else -> PairingCodeState.OPEN
    }
}

/** Secrets of the pairing protocol. Only SHA-256 hashes are stored; plain values are shown once. */
object DeviceSecrets {
    private val random = SecureRandom()

    /** Crockford Base32 without I, L, O, U: easy to read aloud and type. */
    private const val CODE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val CODE_LENGTH = 12
    private const val TOKEN_BYTES = 32
    const val TOKEN_PREFIX = "dvt1_"

    val DEFAULT_CODE_TTL: Duration = Duration.ofHours(24)

    /** e.g. `K7QM-2XRD-9WTF` (60 bits of entropy). */
    fun newPairingCode(): String {
        val raw = buildString { repeat(CODE_LENGTH) { append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]) } }
        return raw.chunked(4).joinToString("-")
    }

    /** Accepts lower case, spaces/dashes and the usual look-alikes (O→0, I/L→1). Null if malformed. */
    fun normalisePairingCode(input: String): String? {
        val compact = input.uppercase().filterNot { it == '-' || it.isWhitespace() }
            .map { when (it) { 'O' -> '0'; 'I', 'L' -> '1'; else -> it } }
            .joinToString("")
        return compact.takeIf { it.length == CODE_LENGTH && it.all(CODE_ALPHABET::contains) }
    }

    fun newDeviceToken(): String {
        val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun sha256(value: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))
}

interface DeviceRepository {
    fun insert(device: Device)
    fun findById(id: UUID): Device?
    fun findActiveByTokenHash(tokenHash: String): Device?
    fun findByBranch(branchId: UUID): List<Device>
    fun findActive(): List<Device>
    fun findActiveByBranch(branchId: UUID): Device?
    fun findActiveByClinic(clinicId: UUID): List<Device>
    fun revoke(id: UUID, reason: String, at: Instant, expectedVersion: Int?): Device
    fun touch(id: UUID, at: Instant, ip: String?)
}

interface PairingCodeRepository {
    fun insert(code: PairingCode)
    fun findById(id: UUID): PairingCode?
    fun findByHashForUpdate(codeHash: String): PairingCode?
    fun findByBranch(branchId: UUID, limit: Int): List<PairingCode>
    fun markClaimed(id: UUID, deviceId: UUID, at: Instant)
    fun cancel(id: UUID, at: Instant): Boolean
    fun cancelOpenForBranch(branchId: UUID, at: Instant)
}
