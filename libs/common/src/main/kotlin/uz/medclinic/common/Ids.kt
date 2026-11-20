package uz.medclinic.common

import com.fasterxml.uuid.Generators
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator
import java.util.UUID

/**
 * Identifier generation shared by every component.
 *
 * UUIDv7 (RFC 9562): time-ordered, so B-tree inserts stay append-mostly, and globally unique, so rows
 * created on different branch servers never collide when they are synchronised later.
 */
object Ids {
    private val generator: TimeBasedEpochGenerator = Generators.timeBasedEpochGenerator()

    fun newId(): UUID = generator.generate()
}
