package uz.medclinic.clinic.cashier.infrastructure

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import uz.medclinic.clinic.cashier.domain.Payment
import uz.medclinic.clinic.cashier.domain.PaymentLine
import uz.medclinic.clinic.cashier.domain.PaymentMethod
import uz.medclinic.clinic.cashier.domain.PaymentRepository
import uz.medclinic.clinic.shared.ClinicContext
import uz.medclinic.platform.persistence.instant
import uz.medclinic.platform.persistence.toDb
import uz.medclinic.platform.persistence.uuid
import java.sql.ResultSet
import java.util.UUID

@Repository
class JdbcPaymentRepository(private val jdbc: JdbcClient, private val clinic: ClinicContext) : PaymentRepository {
    override fun insert(payment: Payment): Payment {
        val identity = clinic.current()
        val receiptNo = jdbc.sql(
            """
            insert into payment (id, clinic_id, branch_id, receipt_no, visit_id, visit_no, patient_id, amount, method, cashier_id,
                                 cashier_name, created_at)
            values (:id, :clinicId, :branchId, nextval('receipt_no_seq'), :visitId, :visitNo, :patientId, :amount, :method,
                    :cashierId, :cashierName, :createdAt)
            returning receipt_no
            """,
        )
            .param("id", payment.id).param("clinicId", identity.clinicId).param("branchId", identity.branchId)
            .param("visitId", payment.visitId).param("visitNo", payment.visitNo).param("patientId", payment.patientId)
            .param("amount", payment.amount).param("method", payment.method.name).param("cashierId", payment.cashierId)
            .param("cashierName", payment.cashierName).param("createdAt", payment.createdAt.toDb())
            .query(Long::class.java).single()
        payment.lines.forEachIndexed { i, line ->
            jdbc.sql(
                """
                insert into payment_line (payment_id, line_no, visit_item_id, service_name, doctor_name, price, discount, amount)
                values (:paymentId, :lineNo, :itemId, :serviceName, :doctorName, :price, :discount, :amount)
                """,
            )
                .param("paymentId", payment.id).param("lineNo", i + 1).param("itemId", line.visitItemId)
                .param("serviceName", line.serviceName).param("doctorName", line.doctorName).param("price", line.price)
                .param("discount", line.discount).param("amount", line.amount).update()
        }
        return payment.copy(receiptNo = receiptNo)
    }

    override fun findById(id: UUID): Payment? =
        withLines(jdbc.sql("select * from payment where id = :id").param("id", id).query(::header).list()).firstOrNull()

    override fun findByVisit(visitId: UUID): List<Payment> =
        withLines(jdbc.sql("select * from payment where visit_id = :visitId order by created_at, receipt_no").param("visitId", visitId).query(::header).list())

    /** A payment row before its lines are loaded. */
    private class Header(val id: UUID, val build: (List<PaymentLine>) -> Payment)

    private fun withLines(headers: List<Header>): List<Payment> {
        if (headers.isEmpty()) return emptyList()
        val lines = jdbc.sql("select * from payment_line where payment_id = any(:ids) order by payment_id, line_no")
            .param("ids", headers.map { it.id }.toTypedArray())
            .query { rs, _ ->
                rs.uuid("payment_id") to PaymentLine(
                    rs.uuid("visit_item_id"), rs.getString("service_name"), rs.getString("doctor_name"),
                    rs.getLong("price"), rs.getLong("discount"), rs.getLong("amount"),
                )
            }.list().groupBy({ it.first }, { it.second })
        return headers.map { it.build(lines[it.id].orEmpty()) }
    }

    private fun header(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int): Header {
        val id = rs.uuid("id")
        val receiptNo = rs.getLong("receipt_no")
        val visitId = rs.uuid("visit_id")
        val visitNo = rs.getLong("visit_no")
        val patientId = rs.uuid("patient_id")
        val method = PaymentMethod.valueOf(rs.getString("method"))
        val cashierId = rs.uuid("cashier_id")
        val cashierName = rs.getString("cashier_name")
        val createdAt = rs.instant("created_at")
        return Header(id) { lines -> Payment(id, receiptNo, visitId, visitNo, patientId, method, cashierId, cashierName, lines, createdAt) }
    }
}
