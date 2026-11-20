package uz.medclinic.vendor.shared.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/** Correlates logs, audit rows and responses with one request id (`X-Request-Id`). */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val requestId = request.getHeader(HEADER)?.takeIf(::isSafe) ?: UUID.randomUUID().toString()
        request.setAttribute(ATTRIBUTE, requestId)
        response.setHeader(HEADER, requestId)
        MDC.put(MDC_KEY, requestId)
        try {
            chain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_KEY)
        }
    }

    private fun isSafe(value: String) = value.length in 8..64 && value.all { it.isLetterOrDigit() || it == '-' }

    companion object {
        const val HEADER = "X-Request-Id"
        const val ATTRIBUTE = "medclinic.requestId"
        const val MDC_KEY = "requestId"
    }
}

/** Information about the current HTTP request, if there is one (null in schedulers). */
data class ClientInfo(val ip: String?, val userAgent: String?, val requestId: String?) {
    companion object {
        private const val MAX_USER_AGENT = 300

        fun current(): ClientInfo {
            val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
                ?: return ClientInfo(null, null, MDC.get(RequestIdFilter.MDC_KEY))
            return of(request)
        }

        fun of(request: HttpServletRequest) = ClientInfo(
            ip = request.remoteAddr,
            userAgent = request.getHeader("User-Agent")?.take(MAX_USER_AGENT),
            requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE) as? String,
        )
    }
}
