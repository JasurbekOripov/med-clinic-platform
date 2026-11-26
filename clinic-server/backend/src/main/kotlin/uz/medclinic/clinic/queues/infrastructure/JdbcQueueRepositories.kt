package uz.medclinic.clinic.queues.infrastructure

import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import uz.medclinic.clinic.queues.domain.Queue
import uz.medclinic.clinic.queues.domain.QueueDetails
import uz.medclinic.clinic.queues.domain.QueueRepository
import uz.medclinic.clinic.queues.domain.Ticket
import uz.medclinic.clinic.queues.domain.TicketItem
import uz.medclinic.clinic.queues.domain.TicketRepository
import uz.medclinic.clinic.queues.domain.TicketStatus
import uz.medclinic.clinic.shared.ClinicContext
import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.persistence.instant
import uz.medclinic.platform.persistence.instantOrNull
import uz.medclinic.platform.persistence.requireVersionMatched
import uz.medclinic.platform.persistence.toDb
import uz.medclinic.platform.persistence.toDbOrNull
import uz.medclinic.platform.persistence.uuid
import uz.medclinic.platform.persistence.uuidOrNull
import java.sql.ResultSet
import java.time.LocalDate
import java.util.UUID

@Repository
class JdbcQueueRepository(private val jdbc: JdbcClient, private val clinic: ClinicContext) : QueueRepository {
    override fun insert(queue: Queue) {
        val identity = clinic.current()
        unique {
            jdbc.sql(
                """
                insert into queue (id, clinic_id, branch_id, name, prefix, room, doctor_id, active, created_at, updated_at, version)
                values (:id, :clinicId, :branchId, :name, :prefix, :room, :doctorId, :active, :createdAt, :updatedAt, 0)
                """,
            ).params(params(queue)).param("clinicId", identity.clinicId).param("branchId", identity.branchId)
                .param("createdAt", queue.createdAt.toDb()).update()
            writeServices(queue)
        }
    }

    override fun findById(id: UUID): Queue? =
        withServices(jdbc.sql("select * from queue where id = :id and deleted_at is null").param("id", id).query(::map).list()).firstOrNull()

    override fun findAll(): List<Queue> =
        withServices(jdbc.sql("select * from queue where deleted_at is null order by prefix, id").query(::map).list())

    override fun update(queue: Queue): Queue {
        val rows = unique {
            jdbc.sql(
                """
                update queue set name = :name, prefix = :prefix, room = :room, doctor_id = :doctorId, active = :active,
                       updated_at = :updatedAt, deleted_at = :deletedAt, version = version + 1
                where id = :id and version = :version and deleted_at is null
                """,
            ).params(params(queue)).param("deletedAt", queue.deletedAt.toDbOrNull()).param("version", queue.version).update()
                .also { if (it > 0) writeServices(queue) }
        }
        requireVersionMatched(rows, "queue", queue.id)
        return queue.copy(version = queue.version + 1)
    }

    /** A deleted queue releases its services, so they can be routed to another queue. */
    private fun writeServices(queue: Queue) {
        jdbc.sql("delete from queue_service where queue_id = :id").param("id", queue.id).update()
        if (queue.deletedAt != null) return
        queue.details.serviceIds.forEach {
            jdbc.sql("insert into queue_service (queue_id, service_id) values (:queue, :service)").param("queue", queue.id).param("service", it).update()
        }
    }

    private fun withServices(queues: List<Queue>): List<Queue> {
        if (queues.isEmpty()) return queues
        val services = jdbc.sql("select * from queue_service where queue_id = any(:ids)").param("ids", queues.map { it.id }.toTypedArray())
            .query { rs, _ -> rs.uuid("queue_id") to rs.uuid("service_id") }.list().groupBy({ it.first }, { it.second })
        return queues.map { q -> q.copy(details = q.details.copy(serviceIds = services[q.id].orEmpty().toSet())) }
    }

    /** The service layer checks names, letters, doctors and services first; this is the guard against races. */
    private inline fun <T> unique(block: () -> T): T =
        try {
            block()
        } catch (_: DuplicateKeyException) {
            throw DomainException("queue.duplicate", DomainException.Kind.CONFLICT)
        }

    private fun params(q: Queue) = mapOf(
        "id" to q.id,
        "name" to q.details.name,
        "prefix" to q.details.prefix,
        "room" to q.details.room,
        "doctorId" to q.details.doctorId,
        "active" to q.details.active,
        "updatedAt" to q.updatedAt.toDb(),
    )

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = Queue(
        id = rs.uuid("id"),
        details = QueueDetails(
            rs.getString("name"), rs.getString("prefix"), rs.getString("room"), rs.uuidOrNull("doctor_id"), emptySet(), rs.getBoolean("active"),
        ),
        createdAt = rs.instant("created_at"),
        updatedAt = rs.instant("updated_at"),
        deletedAt = rs.instantOrNull("deleted_at"),
        version = rs.getInt("version"),
    )
}

@Repository
class JdbcTicketRepository(private val jdbc: JdbcClient, private val clinic: ClinicContext) : TicketRepository {
    override fun nextNumber(queueId: UUID, day: LocalDate): Int =
        jdbc.sql(
            """
            insert into queue_counter (queue_id, day, last_number) values (:queueId, :day, 1)
            on conflict (queue_id, day) do update set last_number = queue_counter.last_number + 1
            returning last_number
            """,
        ).param("queueId", queueId).param("day", day).query(Int::class.java).single()

    override fun insert(ticket: Ticket) {
        val identity = clinic.current()
        jdbc.sql(
            """
            insert into ticket (id, clinic_id, branch_id, queue_id, day, number, visit_id, patient_id, payment_id, status, created_at, version)
            values (:id, :clinicId, :branchId, :queueId, :day, :number, :visitId, :patientId, :paymentId, :status, :createdAt, 0)
            """,
        )
            .param("id", ticket.id).param("clinicId", identity.clinicId).param("branchId", identity.branchId)
            .param("queueId", ticket.queueId).param("day", ticket.day).param("number", ticket.number)
            .param("visitId", ticket.visitId).param("patientId", ticket.patientId).param("paymentId", ticket.paymentId)
            .param("status", ticket.status.name).param("createdAt", ticket.createdAt.toDb()).update()
        ticket.items.forEach {
            jdbc.sql(
                "insert into ticket_item (ticket_id, visit_item_id, service_name, doctor_name, amount) values (:ticket, :item, :service, :doctor, :amount)",
            ).param("ticket", ticket.id).param("item", it.visitItemId).param("service", it.serviceName)
                .param("doctor", it.doctorName).param("amount", it.amount).update()
        }
    }

    override fun findById(id: UUID): Ticket? = load("id = :p", id).firstOrNull()

    override fun findByQueue(queueId: UUID, day: LocalDate): List<Ticket> =
        withItems(
            jdbc.sql("select * from ticket where queue_id = :queueId and day = :day order by number")
                .param("queueId", queueId).param("day", day).query(::map).list(),
        )

    override fun findByPayment(paymentId: UUID): List<Ticket> = load("payment_id = :p", paymentId)

    override fun update(ticket: Ticket): Ticket {
        val rows = jdbc.sql(
            "update ticket set status = :status, done_at = :doneAt, done_by = :doneBy, version = version + 1 where id = :id and version = :version",
        ).param("id", ticket.id).param("status", ticket.status.name).param("doneAt", ticket.doneAt.toDbOrNull())
            .param("doneBy", ticket.doneBy).param("version", ticket.version).update()
        requireVersionMatched(rows, "ticket", ticket.id)
        return ticket.copy(version = ticket.version + 1)
    }

    private fun load(where: String, p: UUID) = withItems(jdbc.sql("select * from ticket where $where order by created_at, id").param("p", p).query(::map).list())

    private fun withItems(tickets: List<Ticket>): List<Ticket> {
        if (tickets.isEmpty()) return tickets
        val items = jdbc.sql("select * from ticket_item where ticket_id = any(:ids) order by service_name")
            .param("ids", tickets.map { it.id }.toTypedArray())
            .query { rs, _ -> rs.uuid("ticket_id") to TicketItem(rs.uuid("visit_item_id"), rs.getString("service_name"), rs.getString("doctor_name"), rs.getLong("amount")) }
            .list().groupBy({ it.first }, { it.second })
        return tickets.map { it.copy(items = items[it.id].orEmpty()) }
    }

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = Ticket(
        id = rs.uuid("id"),
        queueId = rs.uuid("queue_id"),
        day = rs.getObject("day", LocalDate::class.java),
        number = rs.getInt("number"),
        visitId = rs.uuid("visit_id"),
        patientId = rs.uuid("patient_id"),
        paymentId = rs.uuid("payment_id"),
        items = emptyList(),
        status = TicketStatus.valueOf(rs.getString("status")),
        createdAt = rs.instant("created_at"),
        doneAt = rs.instantOrNull("done_at"),
        doneBy = rs.uuidOrNull("done_by"),
        version = rs.getInt("version"),
    )
}
