package uz.medclinic.clinic.catalog.api

import java.util.UUID

/** The price of one service for one order, as visits copy it (name and amounts never change afterwards). */
data class PriceQuote(
    val serviceId: UUID,
    val serviceName: String,
    val groupName: String,
    val doctorId: UUID?,
    val doctorName: String?,
    val price: Long,
)

data class DiscountInfo(val id: UUID, val name: String, val percent: Int)

interface CatalogQueries {
    /**
     * Prices an active service. A per-doctor service (consultation) needs a current doctor and uses the doctor's own
     * price when set; other services may name a doctor too (who performs it) and use the base price.
     * Fails with a validation error for an unknown/inactive service, a missing doctor or a non-doctor.
     */
    fun quote(serviceId: UUID, doctorId: UUID?): PriceQuote

    /** An active discount type, or a validation error. */
    fun discount(id: UUID): DiscountInfo
}
