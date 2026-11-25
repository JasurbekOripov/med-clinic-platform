package uz.medclinic.platform.security

import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

/** Who performs an action: a vendor staff member, a paired Clinic Server, or the system itself. */
interface Actor {
    val actorType: ActorType
    val actorId: UUID?
    val actorName: String
}

enum class ActorType { STAFF, DEVICE, SYSTEM }

data class SimpleActor(
    override val actorType: ActorType,
    override val actorId: UUID?,
    override val actorName: String,
) : Actor {
    companion object {
        val SYSTEM = SimpleActor(ActorType.SYSTEM, null, "system")
    }
}

object CurrentActor {
    /** The authenticated principal if it is an [Actor], otherwise [SimpleActor.SYSTEM] (schedulers, bootstrap). */
    fun get(): Actor = SecurityContextHolder.getContext().authentication?.principal as? Actor ?: SimpleActor.SYSTEM

    fun staffId(): UUID = get().takeIf { it.actorType == ActorType.STAFF }?.actorId
        ?: error("A staff actor is required")
}
