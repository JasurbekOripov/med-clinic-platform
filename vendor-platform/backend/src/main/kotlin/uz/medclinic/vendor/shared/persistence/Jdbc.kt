package uz.medclinic.vendor.shared.persistence

import uz.medclinic.vendor.shared.domain.VersionConflictException
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** Small JDBC helpers: PostgreSQL timestamptz ↔ Instant (always UTC) and uuid columns. */
fun Instant.toDb(): OffsetDateTime = atOffset(ZoneOffset.UTC)

fun Instant?.toDbOrNull(): OffsetDateTime? = this?.toDb()

fun ResultSet.instant(column: String): Instant = getObject(column, OffsetDateTime::class.java).toInstant()

fun ResultSet.instantOrNull(column: String): Instant? = getObject(column, OffsetDateTime::class.java)?.toInstant()

fun ResultSet.uuid(column: String): UUID = getObject(column, UUID::class.java)

fun ResultSet.uuidOrNull(column: String): UUID? = getObject(column, UUID::class.java)

fun ResultSet.intOrNull(column: String): Int? = getInt(column).takeUnless { wasNull() }

fun ResultSet.longOrNull(column: String): Long? = getLong(column).takeUnless { wasNull() }

/** For `update … where id = ? and version = ?`: zero rows means a concurrent change (or a missing row). */
fun requireVersionMatched(updatedRows: Int, entity: String, id: UUID) {
    if (updatedRows == 0) throw VersionConflictException(entity, id)
}

/** Escapes a user search term for `ILIKE '%…%' ESCAPE '\'`. */
fun likePattern(term: String): String =
    "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
