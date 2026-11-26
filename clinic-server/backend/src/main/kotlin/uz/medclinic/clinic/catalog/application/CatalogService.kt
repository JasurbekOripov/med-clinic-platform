package uz.medclinic.clinic.catalog.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.clinic.audit.api.AuditEvent
import uz.medclinic.clinic.audit.api.AuditRecorder
import uz.medclinic.clinic.catalog.api.CatalogQueries
import uz.medclinic.clinic.catalog.api.DiscountInfo
import uz.medclinic.clinic.catalog.api.PriceQuote
import uz.medclinic.clinic.catalog.domain.CatalogConflict
import uz.medclinic.clinic.catalog.domain.DiscountDetails
import uz.medclinic.clinic.catalog.domain.DiscountType
import uz.medclinic.clinic.catalog.domain.DiscountTypeRepository
import uz.medclinic.clinic.catalog.domain.ServiceDetails
import uz.medclinic.clinic.catalog.domain.ServiceGroup
import uz.medclinic.clinic.catalog.domain.ServiceGroupRepository
import uz.medclinic.clinic.catalog.domain.ServiceItem
import uz.medclinic.clinic.catalog.domain.ServiceItemRepository
import uz.medclinic.clinic.identity.api.StaffDirectory
import uz.medclinic.clinic.identity.api.StaffRef
import uz.medclinic.clinic.shared.Permission
import uz.medclinic.common.Ids
import uz.medclinic.platform.domain.NotFoundException
import uz.medclinic.platform.domain.ValidationException
import uz.medclinic.platform.domain.VersionConflictException
import java.time.Clock
import java.util.UUID

/** Price list and discount types. Every change is audited (who changed which price, before → after). */
@Service
class CatalogService(
    private val groups: ServiceGroupRepository,
    private val services: ServiceItemRepository,
    private val discounts: DiscountTypeRepository,
    private val staff: StaffDirectory,
    private val audit: AuditRecorder,
    private val clock: Clock,
) : CatalogQueries {
    // ---- queries for other modules

    @Transactional(readOnly = true)
    override fun quote(serviceId: UUID, doctorId: UUID?): PriceQuote {
        val service = services.findById(serviceId)?.takeIf { it.details.active }
            ?: throw ValidationException("serviceId", "catalog.unknown_service")
        val group = groups.findById(service.details.groupId) ?: throw ValidationException("serviceId", "catalog.unknown_service")
        if (service.details.perDoctor && doctorId == null) throw ValidationException("doctorId", "catalog.doctor_required")
        val doctor = doctorId?.let { id -> doctors().firstOrNull { it.id == id } ?: throw ValidationException("doctorId", "catalog.unknown_doctor") }
        val price = if (service.details.perDoctor) service.details.priceFor(doctorId) else service.details.price
        return PriceQuote(service.id, service.details.name, group.name, doctor?.id, doctor?.fullName, price)
    }

    @Transactional(readOnly = true)
    override fun discount(id: UUID): DiscountInfo {
        val d = discounts.findById(id)?.takeIf { it.active } ?: throw ValidationException("discountTypeId", "catalog.unknown_discount")
        return DiscountInfo(d.id, d.name, d.percent)
    }

    // ---- groups

    @Transactional(readOnly = true)
    fun groups(): List<ServiceGroup> = groups.findAll()

    @Transactional
    fun createGroup(name: String?, sortOrder: Int): ServiceGroup {
        val now = clock.instant()
        val group = ServiceGroup(Ids.newId(), ServiceGroup.validName(name), sortOrder, now, now, null, 0)
        groups.insert(group)
        audit.record(AuditEvent("service_group.created", GROUP, group.id, after = snapshot(group)))
        return group
    }

    @Transactional
    fun updateGroup(id: UUID, version: Int, name: String?, sortOrder: Int): ServiceGroup {
        val current = checked(groups.findById(id), GROUP, id, version) { it.version }
        val updated = groups.update(current.renamed(ServiceGroup.validName(name), sortOrder, clock.instant()))
        audit.record(AuditEvent("service_group.updated", GROUP, id, before = snapshot(current), after = snapshot(updated)))
        return updated
    }

    /** Only empty groups can be deleted, so no service silently disappears from the price list. */
    @Transactional
    fun deleteGroup(id: UUID, version: Int) {
        val current = checked(groups.findById(id), GROUP, id, version) { it.version }
        if (groups.countServices(id) > 0) throw CatalogConflict("catalog.group_not_empty")
        groups.update(current.deleted(clock.instant()))
        audit.record(AuditEvent("service_group.deleted", GROUP, id, before = snapshot(current)))
    }

    // ---- services

    @Transactional(readOnly = true)
    fun services(): List<ServiceItem> = services.findAll()

    /** Who can have an own consultation price: active staff allowed to write consultations. */
    @Transactional(readOnly = true)
    fun doctors(): List<StaffRef> = staff.activeWith(Permission.CONSULTATION_WRITE)

    @Transactional
    fun createService(details: ServiceDetails): ServiceItem {
        validateReferences(details)
        val now = clock.instant()
        val item = ServiceItem(Ids.newId(), details, now, now, null, 0)
        services.insert(item)
        audit.record(AuditEvent("service.created", SERVICE, item.id, after = snapshot(item)))
        return item
    }

    @Transactional
    fun updateService(id: UUID, version: Int, details: ServiceDetails): ServiceItem {
        val current = checked(services.findById(id), SERVICE, id, version) { it.version }
        validateReferences(details, keepDoctors = current.details.doctorPrices.keys)
        val updated = services.update(current.withDetails(details, clock.instant()))
        audit.record(AuditEvent("service.updated", SERVICE, id, before = snapshot(current), after = snapshot(updated)))
        return updated
    }

    /** Soft delete; past visits keep their own copy of name and price. To hide a service temporarily, deactivate it. */
    @Transactional
    fun deleteService(id: UUID, version: Int) {
        val current = checked(services.findById(id), SERVICE, id, version) { it.version }
        services.update(current.deleted(clock.instant()))
        audit.record(AuditEvent("service.deleted", SERVICE, id, before = snapshot(current)))
    }

    // ---- discount types

    @Transactional(readOnly = true)
    fun discounts(): List<DiscountType> = discounts.findAll()

    @Transactional
    fun createDiscount(details: DiscountDetails): DiscountType {
        val now = clock.instant()
        val discount = DiscountType(Ids.newId(), details.name, details.percent, details.active, now, now, null, 0)
        discounts.insert(discount)
        audit.record(AuditEvent("discount_type.created", DISCOUNT, discount.id, after = snapshot(discount)))
        return discount
    }

    @Transactional
    fun updateDiscount(id: UUID, version: Int, details: DiscountDetails): DiscountType {
        val current = checked(discounts.findById(id), DISCOUNT, id, version) { it.version }
        val updated = discounts.update(current.changed(details, clock.instant()))
        audit.record(AuditEvent("discount_type.updated", DISCOUNT, id, before = snapshot(current), after = snapshot(updated)))
        return updated
    }

    @Transactional
    fun deleteDiscount(id: UUID, version: Int) {
        val current = checked(discounts.findById(id), DISCOUNT, id, version) { it.version }
        discounts.update(current.deleted(clock.instant()))
        audit.record(AuditEvent("discount_type.deleted", DISCOUNT, id, before = snapshot(current)))
    }

    // ---- helpers

    /**
     * The group must exist; doctor prices only for current doctors. Prices of a doctor who has since left (deactivated)
     * may stay on an edited service, so saving never fails because of someone else's staff change.
     */
    private fun validateReferences(details: ServiceDetails, keepDoctors: Set<UUID> = emptySet()) {
        if (groups.findById(details.groupId) == null) throw ValidationException("groupId", "catalog.unknown_group")
        val doctorIds = doctors().map { it.id }.toSet() + keepDoctors
        val unknown = details.doctorPrices.keys - doctorIds
        if (unknown.isNotEmpty()) throw ValidationException("doctorPrices", "catalog.unknown_doctor")
    }

    private inline fun <T : Any> checked(found: T?, entity: String, id: UUID, version: Int, versionOf: (T) -> Int): T {
        val item = found ?: throw NotFoundException(entity, id)
        if (versionOf(item) != version) throw VersionConflictException(entity, id)
        return item
    }

    private fun snapshot(g: ServiceGroup) = mapOf("name" to g.name, "sortOrder" to g.sortOrder)

    private fun snapshot(s: ServiceItem) = mapOf(
        "groupId" to s.details.groupId, "code" to s.details.code, "name" to s.details.name, "price" to s.details.price,
        "perDoctor" to s.details.perDoctor, "doctorPrices" to s.details.doctorPrices.mapKeys { it.key.toString() }.toSortedMap(),
        "active" to s.details.active,
    )

    private fun snapshot(d: DiscountType) = mapOf("name" to d.name, "percent" to d.percent, "active" to d.active)

    private companion object {
        const val GROUP = "service_group"
        const val SERVICE = "service"
        const val DISCOUNT = "discount_type"
    }
}
