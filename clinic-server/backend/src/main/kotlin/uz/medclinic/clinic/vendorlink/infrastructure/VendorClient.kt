package uz.medclinic.clinic.vendorlink.infrastructure

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.json.JsonMapper
import uz.medclinic.clinic.vendorlink.VendorLinkProperties
import uz.medclinic.platform.domain.DomainException
import uz.medclinic.vendorprotocol.DeviceApi
import uz.medclinic.vendorprotocol.HeartbeatRequest
import uz.medclinic.vendorprotocol.HeartbeatResponse
import uz.medclinic.vendorprotocol.PairingClaimRequest
import uz.medclinic.vendorprotocol.PairingClaimResponse
import java.net.http.HttpClient

/**
 * Outgoing-only HTTPS calls to the Vendor Platform. Vendor errors keep their stable `code` (e.g.
 * `pairing.invalid_code`); network problems become `vendor.unreachable`.
 */
@Component
class VendorClient(private val properties: VendorLinkProperties, private val mapper: JsonMapper) {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun client(baseUrl: String): RestClient {
        // JDK HttpClient: unlike HttpURLConnection it exposes error bodies of 401 responses (the vendor's error code).
        val http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout).build()
        val factory = JdkClientHttpRequestFactory(http).apply { setReadTimeout(properties.readTimeout) }
        return RestClient.builder().baseUrl(baseUrl.trimEnd('/')).requestFactory(factory).build()
    }

    fun claim(baseUrl: String, request: PairingClaimRequest): PairingClaimResponse = call {
        client(baseUrl).post().uri(DeviceApi.PAIRING_CLAIM_PATH).body(request).retrieve().body(PairingClaimResponse::class.java)!!
    }

    fun heartbeat(baseUrl: String, token: String, request: HeartbeatRequest): HeartbeatResponse = call {
        client(baseUrl).post().uri(DeviceApi.HEARTBEATS_PATH).header(HttpHeaders.AUTHORIZATION, bearer(token)).body(request)
            .retrieve().body(HeartbeatResponse::class.java)!!
    }

    fun license(baseUrl: String, token: String): ByteArray = call {
        client(baseUrl).get().uri(DeviceApi.LICENSE_PATH).header(HttpHeaders.AUTHORIZATION, bearer(token))
            .retrieve().body(ByteArray::class.java)!!
    }

    private fun bearer(token: String) = "${DeviceApi.AUTHORIZATION_SCHEME} $token"

    private fun <T> call(block: () -> T): T = try {
        block()
    } catch (ex: RestClientResponseException) {
        throw DomainException(vendorCode(ex), kindOf(ex.statusCode))
    } catch (ex: ResourceAccessException) {
        log.warn("Vendor platform unreachable: {}", ex.message)
        throw DomainException("vendor.unreachable", DomainException.Kind.RULE_VIOLATION)
    }

    private fun vendorCode(ex: RestClientResponseException): String =
        runCatching { mapper.readTree(ex.responseBodyAsByteArray).path("code").asString() }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: "vendor.error_${ex.statusCode.value()}"

    private fun kindOf(status: HttpStatusCode): DomainException.Kind = when (status.value()) {
        401, 403 -> DomainException.Kind.UNAUTHORIZED
        404 -> DomainException.Kind.NOT_FOUND
        409 -> DomainException.Kind.CONFLICT
        429 -> DomainException.Kind.TOO_MANY_REQUESTS
        else -> DomainException.Kind.RULE_VIOLATION
    }
}
