package uz.medclinic.clinic.cashier.infrastructure

import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import uz.medclinic.clinic.cashier.domain.Expense
import uz.medclinic.clinic.cashier.domain.ExpenseRepository
import uz.medclinic.clinic.cashier.domain.PaymentMethod
import uz.medclinic.clinic.cashier.domain.Refund
import uz.medclinic.clinic.cashier.domain.RefundRepository
import uz.medclinic.clinic.cashier.domain.RefundStatus
import uz.medclinic.clinic.cashier.domain.Shift
import uz.medclinic.clinic.cashier.domain.ShiftRepository
import uz.medclinic.clinic.cashier.domain.ShiftStatus
import uz.medclinic.clinic.cashier.domain.ShiftTotals
import uz.medclinic.clinic.shared.ClinicContext
import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.persistence.instant
import uz.medclinic.platform.persistence.instantOrNull
import uz.medclinic.platform.persistence.longOrNull
import uz.medclinic.platform.persistence.requireVersionMatched
import uz.medclinic.platform.persistence.toDb
import uz.medclinic.platform.persistence.toDbOrNull
import uz.medclinic.platform.persistence.uuid
import uz.medclinic.platform.persistence.uuidOrNull
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@Repository
class JdbcShiftRepository(private val jdbc: JdbcClient, private val clinic: ClinicContext) : ShiftRepository {
    override fun insert(shift: Shift): Shift {
        val identity = clinic.current()
        val no = try {
            jdbc.sql(
                """
                insert into cash_shift (id, clinic_id, branch_id, shift_no, cashier_id, cashier_name, opening_cash, opened_at, status, version)
                values (:id, :clinicId, :branchId, nextval('cash_shift_no_seq'), :cashierId, :cashierName, :opening, :openedAt, 'OPEN', 0)
                returning shift_no
                """,
            ).param("id", shift.id).param("clinicId", identity.clinicId).param("branchId", identity.branchId)
                .param("cashierId", shift.cashierId).param("cashierName", shift.cashierName).param("opening", shift.openingCash)
                .param("openedAt", shift.openedAt.toDb()).query(Long::class.java).single()
        } catch (_: DuplicateKeyException) {
            throw DomainException("shift.already_open", DomainException.Kind.CONFLICT)
        }
        return shift.copy(shiftNo = no)
    }

    override fun findById(id: UUID): Shift? = jdbc.sql("select * from cash_shift where id = :id").param("id", id).query(::map).optional().orElse(null)

    override fun findOpen(cashierId: UUID): Shift? =
        jdbc.sql("select * from cash_shift where cashier_id = :c and status = 'OPEN'").param("c", cashierId).query(::map).optional().orElse(null)

    override fun lock(id: UUID): Shift? = jdbc.sql("select * from cash_shift where id = :id for update").param("id", id).query(::map).optional().orElse(null)

    override fun update(shift: Shift): Shift {
        val rows = jdbc.sql(
            """
            update cash_shift set status = :status, closed_at = :closedAt, expected_cash = :expected, counted_cash = :counted,
                   close_note = :note, version = version + 1
            where id = :id and version = :version
            """,
        ).param("id", shift.id).param("status", shift.status.name).param("closedAt", shift.closedAt.toDbOrNull())
            .param("expected", shift.expectedCash).param("counted", shift.countedCash).param("note", shift.closeNote)
            .param("version", shift.version).update()
        requireVersionMatched(rows, "cash_shift", shift.id)
        return shift.copy(version = shift.version + 1)
    }

    override fun totals(shiftId: UUID): ShiftTotals =
        jdbc.sql(
            """
            select
              coalesce((select sum(amount) from payment where shift_id = :s and method = 'CASH'), 0) as cash,
              coalesce((select sum(amount) from payment where shift_id = :s and method = 'CARD'), 0) as card,
              coalesce((select sum(amount) from payment where shift_id = :s and method = 'TRANSFER'), 0) as transfer,
              (select count(*) from payment where shift_id = :s) as payments,
              coalesce((select sum(amount) from refund where shift_id = :s), 0) as refunds,
              (select count(*) from refund where shift_id = :s) as refund_count,
              coalesce((select sum(amount) from cash_expense where shift_id = :s), 0) as expenses,
              (select count(*) from cash_expense where shift_id = :s) as expense_count
            """,
        ).param("s", shiftId).query { rs, _ ->
            ShiftTotals(
                rs.getLong("cash"), rs.getLong("card"), rs.getLong("transfer"), rs.getInt("payments"),
                rs.getLong("refunds"), rs.getInt("refund_count"), rs.getLong("expenses"), rs.getInt("expense_count"),
            )
        }.single()

    override fun list(from: Instant, to: Instant): List<Shift> =
        jdbc.sql("select * from cash_shift where opened_at >= :from and opened_at < :to order by opened_at desc, id desc limit 500")
            .param("from", from.toDb()).param("to", to.toDb()).query(::map).list()

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = Shift(
        id = rs.uuid("id"),
        shiftNo = rs.getLong("shift_no"),
        cashierId = rs.uuid("cashier_id"),
        cashierName = rs.getString("cashier_name"),
        openingCash = rs.getLong("opening_cash"),
        openedAt = rs.instant("opened_at"),
        status = ShiftStatus.valueOf(rs.getString("status")),
        closedAt = rs.instantOrNull("closed_at"),
        expectedCash = rs.longOrNull("expected_cash"),
        countedCash = rs.longOrNull("counted_cash"),
        closeNote = rs.getString("close_note"),
        version = rs.getInt("version"),
    )
}

@Repository
class JdbcExpenseRepository(private val jdbc: JdbcClient, private val clinic: ClinicContext) : ExpenseRepository {
    override fun insert(expense: Expense) {
        val identity = clinic.current()
        jdbc.sql(
            """
            insert into cash_expense (id, clinic_id, branch_id, shift_id, recipient_staff_id, recipient_name, amount, comment, created_by, created_at)
            values (:id, :clinicId, :branchId, :shiftId, :staffId, :name, :amount, :comment, :by, :at)
            """,
        ).param("id", expense.id).param("clinicId", identity.clinicId).param("branchId", identity.branchId)
            .param("shiftId", expense.shiftId).param("staffId", expense.recipientStaffId).param("name", expense.recipientName)
            .param("amount", expense.amount).param("comment", expense.comment).param("by", expense.createdBy)
            .param("at", expense.createdAt.toDb()).update()
    }

    override fun byShift(shiftId: UUID): List<Expense> =
        jdbc.sql("select * from cash_expense where shift_id = :s order by created_at, id").param("s", shiftId).query { rs, _ ->
            Expense(
                rs.uuid("id"), rs.uuid("shift_id"), rs.uuidOrNull("recipient_staff_id"), rs.getString("recipient_name"),
                rs.getLong("amount"), rs.getString("comment"), rs.uuid("created_by"), rs.instant("created_at"),
            )
        }.list()
}

@Repository
class JdbcRefundRepository(private val jdbc: JdbcClient, private val clinic: ClinicContext) : RefundRepository {
    override fun insert(refund: Refund) {
        val identity = clinic.current()
        try {
            jdbc.sql(
                """
                insert into refund (id, clinic_id, branch_id, payment_id, visit_id, visit_item_id, patient_id, service_name, amount,
                                    original_method, reason, approved_by, approved_by_name, approved_at, status, version)
                values (:id, :clinicId, :branchId, :paymentId, :visitId, :itemId, :patientId, :serviceName, :amount,
                        :method, :reason, :approvedBy, :approvedByName, :approvedAt, 'APPROVED', 0)
                """,
            ).param("id", refund.id).param("clinicId", identity.clinicId).param("branchId", identity.branchId)
                .param("paymentId", refund.paymentId).param("visitId", refund.visitId).param("itemId", refund.visitItemId)
                .param("patientId", refund.patientId).param("serviceName", refund.serviceName).param("amount", refund.amount)
                .param("method", refund.originalMethod.name).param("reason", refund.reason).param("approvedBy", refund.approvedBy)
                .param("approvedByName", refund.approvedByName).param("approvedAt", refund.approvedAt.toDb()).update()
        } catch (_: DuplicateKeyException) {
            throw DomainException("refund.already_exists", DomainException.Kind.CONFLICT)
        }
    }

    override fun findById(id: UUID): Refund? = jdbc.sql("select * from refund where id = :id").param("id", id).query(::map).optional().orElse(null)

    override fun update(refund: Refund): Refund {
        val rows = jdbc.sql(
            """
            update refund set status = :status, shift_id = :shiftId, paid_out_by = :by, paid_out_at = :at, version = version + 1
            where id = :id and version = :version
            """,
        ).param("id", refund.id).param("status", refund.status.name).param("shiftId", refund.shiftId).param("by", refund.paidOutBy)
            .param("at", refund.paidOutAt.toDbOrNull()).param("version", refund.version).update()
        requireVersionMatched(rows, "refund", refund.id)
        return refund.copy(version = refund.version + 1)
    }

    override fun pending(): List<Refund> =
        jdbc.sql("select * from refund where status = 'APPROVED' order by approved_at, id").query(::map).list()

    override fun byShift(shiftId: UUID): List<Refund> =
        jdbc.sql("select * from refund where shift_id = :s order by paid_out_at, id").param("s", shiftId).query(::map).list()

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = Refund(
        id = rs.uuid("id"),
        paymentId = rs.uuid("payment_id"),
        visitId = rs.uuid("visit_id"),
        visitItemId = rs.uuid("visit_item_id"),
        patientId = rs.uuid("patient_id"),
        serviceName = rs.getString("service_name"),
        amount = rs.getLong("amount"),
        originalMethod = PaymentMethod.valueOf(rs.getString("original_method")),
        reason = rs.getString("reason"),
        approvedBy = rs.uuid("approved_by"),
        approvedByName = rs.getString("approved_by_name"),
        approvedAt = rs.instant("approved_at"),
        status = RefundStatus.valueOf(rs.getString("status")),
        shiftId = rs.uuidOrNull("shift_id"),
        paidOutBy = rs.uuidOrNull("paid_out_by"),
        paidOutAt = rs.instantOrNull("paid_out_at"),
        version = rs.getInt("version"),
    )
}
