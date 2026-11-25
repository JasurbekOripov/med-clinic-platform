package uz.medclinic.clinic.support

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/** A tiny stand-in for the Vendor Platform's device API, scripted per test. */
class FakeVendor : AutoCloseable {
    data class Call(val method: String, val path: String, val authorization: String?, val body: String)

    data class Reply(val status: Int, val body: String, val contentType: String = "application/json")

    val calls = CopyOnWriteArrayList<Call>()
    private val routes = mutableMapOf<String, () -> Reply>()
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange -> handle(exchange) }
        start()
    }

    val url: String get() = "http://127.0.0.1:${server.address.port}"

    fun on(method: String, path: String, reply: () -> Reply) {
        routes["$method $path"] = reply
    }

    private fun handle(exchange: HttpExchange) {
        val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
        calls += Call(exchange.requestMethod, exchange.requestURI.path, exchange.requestHeaders.getFirst("Authorization"), body)
        val reply = routes["${exchange.requestMethod} ${exchange.requestURI.path}"]?.invoke()
            ?: Reply(404, """{"code":"common.not_found"}""")
        val bytes = reply.body.toByteArray()
        exchange.responseHeaders.add("Content-Type", reply.contentType)
        exchange.sendResponseHeaders(reply.status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    override fun close() = server.stop(0)
}
