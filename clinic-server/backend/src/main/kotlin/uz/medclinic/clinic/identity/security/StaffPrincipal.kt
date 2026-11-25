package uz.medclinic.clinic.identity.security

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticatedPrincipal
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import uz.medclinic.platform.security.Actor
import uz.medclinic.platform.security.ActorType
import java.io.Serial
import java.io.Serializable
import java.time.Instant
import java.util.UUID

/**
 * The signed-in staff member, stored in the server-side session. [permissions] is a snapshot: any change to
 * the person's role or overrides ends their sessions, so a stale snapshot never outlives a change.
 */
data class StaffPrincipal(
    val id: UUID,
    val login: String,
    val fullName: String,
    val roleId: UUID,
    val roleTemplate: String?,
    val roleName: String?,
    val permissions: Set<String>,
    val mustChangePassword: Boolean,
    val authenticatedAt: Instant,
) : AuthenticatedPrincipal, Actor, Serializable {
    override fun getName(): String = id.toString()
    override val actorType: ActorType get() = ActorType.STAFF
    override val actorId: UUID get() = id
    override val actorName: String get() = "$fullName ($login)"

    fun toAuthentication(): Authentication =
        UsernamePasswordAuthenticationToken.authenticated(this, null, permissions.map(::SimpleGrantedAuthority))

    private companion object {
        @Serial
        private const val serialVersionUID: Long = 1L
    }
}
