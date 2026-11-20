package uz.medclinic.vendor.identity.infrastructure

import org.springframework.session.FindByIndexNameSessionRepository
import org.springframework.session.Session
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.UUID

/**
 * Ends server-side sessions of a staff member (deactivation, role change, password change/reset).
 * Runs after the surrounding transaction commits, so a rolled-back change never logs anyone out.
 */
@Component
class SessionTerminator(private val sessions: FindByIndexNameSessionRepository<out Session>) {
    fun terminateAll(staffId: UUID, exceptSessionId: String? = null) {
        val action = Runnable {
            sessions.findByPrincipalName(staffId.toString()).keys
                .filter { it != exceptSessionId }
                .forEach(sessions::deleteById)
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = action.run()
            })
        } else {
            action.run()
        }
    }
}
