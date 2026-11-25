package uz.medclinic.vendor.identity.security

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticatedPrincipal
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import uz.medclinic.vendor.identity.domain.StaffAccount
import uz.medclinic.vendor.identity.domain.VendorRole
import uz.medclinic.platform.security.Actor
import uz.medclinic.platform.security.ActorType
import uz.medclinic.vendor.shared.security.Permission
import java.io.Serial
import java.io.Serializable
import java.time.Instant
import java.util.UUID

/**
 * The authenticated staff member stored in the server-side session. Its name is the staff id, which is
 * what Spring Session indexes, so all sessions of a person can be found and terminated.
 */
data class StaffPrincipal(
    val id: UUID,
    val login: String,
    val fullName: String,
    val role: VendorRole,
    val mustChangePassword: Boolean,
    val authenticatedAt: Instant,
) : AuthenticatedPrincipal, Actor, Serializable {
    val permissions: Set<Permission> get() = role.permissions

    override fun getName(): String = id.toString()
    override val actorType: ActorType get() = ActorType.STAFF
    override val actorId: UUID get() = id
    override val actorName: String get() = "$fullName ($login)"

    fun toAuthentication(): Authentication =
        UsernamePasswordAuthenticationToken.authenticated(this, null, permissions.map { SimpleGrantedAuthority(it.name) })

    companion object {
        @Serial
        private const val serialVersionUID: Long = 1L

        fun of(staff: StaffAccount, authenticatedAt: Instant) =
            StaffPrincipal(staff.id, staff.login, staff.fullName, staff.role, staff.mustChangePassword, authenticatedAt)
    }
}
