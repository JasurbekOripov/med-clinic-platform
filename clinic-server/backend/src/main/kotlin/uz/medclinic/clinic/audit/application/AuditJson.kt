package uz.medclinic.clinic.audit.application

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

/** Serialises audit snapshots and removes anything that looks like a credential. */
internal class AuditJson(private val mapper: JsonMapper) {
    fun toJson(value: Any?): String? {
        if (value == null) return null
        val tree: JsonNode = mapper.valueToTree(value)
        return mapper.writeValueAsString(redact(tree))
    }

    private fun redact(node: JsonNode): JsonNode {
        when {
            node.isObject -> {
                val obj = node as ObjectNode
                val secretKeys = obj.propertyNames().filter { SECRET_KEY.containsMatchIn(it) }
                secretKeys.forEach { obj.put(it, REDACTED) }
                obj.properties().forEach { (key, child) -> if (key !in secretKeys) redact(child) }
            }
            node.isArray -> node.forEach(::redact)
        }
        return node
    }

    private companion object {
        val SECRET_KEY = Regex("password|secret|token|hash|privatekey|signature", RegexOption.IGNORE_CASE)
        const val REDACTED = "***"
    }
}
