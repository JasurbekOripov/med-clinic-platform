package uz.medclinic.clinic.catalog.domain

import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.domain.FieldViolation
import uz.medclinic.platform.domain.Text
import uz.medclinic.platform.domain.ValidationException
import java.time.Instant
import java.util.UUID

/** Upper bound for any single price: catches a mistyped extra digit or two (1 billion UZS). */
const val MAX_PRICE: Long = 1_000_000_000

private class Errors {
    val list = mutableListOf<FieldViolation>()

    fun text(field: String, value: String?, max: Int, required: Boolean): String? {
        val clean = Text.clean(value)
        if (clean == null && required) list += FieldViolation(field, "validation.not_blank")
        if (clean != null && clean.length > max) list += FieldViolation(field, "validation.size", mapOf("max" to max))
        return clean
    }

    fun price(field: String, value: Long?) {
        when {
            value == null -> list += FieldViolation(field, "validation.not_null")
            value < 0 || value > MAX_PRICE -> list += FieldViolation(field, "validation.range", mapOf("min" to 0, "max" to MAX_PRICE))
        }
    }

    fun throwIfAny() {
        if (list.isNotEmpty()) throw ValidationException(list)
    }
}

data class ServiceGroup(
    val id: UUID,
    val name: String,
    val sortOrder: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val version: Int,
) {
    fun renamed(name: String, sortOrder: Int, now: Instant) = copy(name = name, sortOrder = sortOrder, updatedAt = now)

    fun deleted(now: Instant) = copy(deletedAt = now, updatedAt = now)

    companion object {
        fun validName(raw: String?): String {
            val e = Errors()
            val name = e.text("name", raw, 100, required = true)
            e.throwIfAny()
            return name!!
        }
    }
}

/** Editable fields of a price list entry, validated. */
data class ServiceDetails(
    val groupId: UUID,
    val code: String?,
    val name: String,
    val price: Long,
    val perDoctor: Boolean,
    /** Own prices of single doctors (staff id → UZS); only for [perDoctor] services. Others pay [price]. */
    val doctorPrices: Map<UUID, Long>,
    val active: Boolean,
) {
    /** Price for an order: the doctor's own price if set, otherwise the base price. */
    fun priceFor(doctorId: UUID?): Long = doctorId?.let { doctorPrices[it] } ?: price

    companion object {
        private val CODE = Regex("[A-Za-z0-9.\\-]+")

        fun of(
            groupId: UUID?,
            code: String?,
            name: String?,
            price: Long?,
            perDoctor: Boolean,
            doctorPrices: Map<UUID, Long>,
            active: Boolean,
        ): ServiceDetails {
            val e = Errors()
            if (groupId == null) e.list += FieldViolation("groupId", "validation.not_null")
            val cleanCode = e.text("code", code, 20, required = false)
            if (cleanCode != null && !CODE.matches(cleanCode)) e.list += FieldViolation("code", "validation.service_code")
            val cleanName = e.text("name", name, 200, required = true)
            e.price("price", price)
            if (!perDoctor && doctorPrices.isNotEmpty()) e.list += FieldViolation("doctorPrices", "catalog.doctor_prices_not_per_doctor")
            doctorPrices.forEach { (staffId, p) -> e.price("doctorPrices.$staffId", p) }
            e.throwIfAny()
            return ServiceDetails(groupId!!, cleanCode, cleanName!!, price!!, perDoctor, doctorPrices, active)
        }
    }
}

data class ServiceItem(
    val id: UUID,
    val details: ServiceDetails,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val version: Int,
) {
    fun withDetails(details: ServiceDetails, now: Instant) = copy(details = details, updatedAt = now)

    fun deleted(now: Instant) = copy(deletedAt = now, updatedAt = now)
}

/** A named discount the Admin defines (e.g. "Pensioner 20%"); Reception picks one from a list for a visit. */
data class DiscountType(
    val id: UUID,
    val name: String,
    val percent: Int,
    val active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val version: Int,
) {
    fun changed(details: DiscountDetails, now: Instant) = copy(name = details.name, percent = details.percent, active = details.active, updatedAt = now)

    fun deleted(now: Instant) = copy(deletedAt = now, updatedAt = now)

    /** Discount in UZS for [amount], rounded half up to whole sum, never more than the amount. */
    fun discountOf(amount: Long): Long = DiscountMath.percentOf(amount, percent)
}

data class DiscountDetails(val name: String, val percent: Int, val active: Boolean) {
    companion object {
        fun of(name: String?, percent: Int?, active: Boolean): DiscountDetails {
            val e = Errors()
            val cleanName = e.text("name", name, 100, required = true)
            if (percent == null) e.list += FieldViolation("percent", "validation.not_null")
            else if (percent !in 1..100) e.list += FieldViolation("percent", "validation.range", mapOf("min" to 1, "max" to 100))
            e.throwIfAny()
            return DiscountDetails(cleanName!!, percent!!, active)
        }
    }
}

object DiscountMath {
    /** `amount × percent / 100`, rounded half up, exact integer arithmetic (no floating point for money). */
    fun percentOf(amount: Long, percent: Int): Long {
        require(amount >= 0 && percent in 0..100)
        return Math.addExact(Math.multiplyExact(amount, percent.toLong()), 50) / 100
    }
}

interface ServiceGroupRepository {
    fun insert(group: ServiceGroup)
    fun findById(id: UUID): ServiceGroup?
    fun findAll(): List<ServiceGroup>
    fun update(group: ServiceGroup): ServiceGroup
    fun countServices(groupId: UUID): Long
}

interface ServiceItemRepository {
    fun insert(item: ServiceItem)
    fun findById(id: UUID): ServiceItem?
    fun findAll(): List<ServiceItem>
    fun update(item: ServiceItem): ServiceItem
}

interface DiscountTypeRepository {
    fun insert(discount: DiscountType)
    fun findById(id: UUID): DiscountType?
    fun findAll(): List<DiscountType>
    fun update(discount: DiscountType): DiscountType
}

class CatalogConflict(code: String) : DomainException(code, Kind.CONFLICT)
